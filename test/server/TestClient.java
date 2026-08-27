package server;

import net.Connection;
import net.Envelope;
import net.Packet;
import net.PacketCodec;
import net.Protocol;
import net.packets.HelloRequest;
import net.packets.HelloResponse;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

// A minimal client, for tests that need a real socket on the other end of a real server.
//
// Correlation ids are used exactly as the shipped client will, and for the same reason: waiting for a
// reply must not be "read the next line and hope". The moment the server pushes anything unsolicited
// -- a challenge invite, a snapshot, a reaction -- the next line is very often not the answer.
public final class TestClient implements AutoCloseable {

    // Generous by the standards of a loopback socket, which answers in microseconds. Sized for a loaded
    // CI machine rather than for the happy path: a flaky timeout in a networking test teaches people to
    // re-run the suite instead of reading it.
    public static final long REPLY_TIMEOUT_MS = 5_000;

    private final Connection connection;
    // Replies and pushes are kept APART, which matters as soon as the server sends anything
    // unsolicited. A challenge invite landing between a request and its answer would otherwise be
    // handed back as the answer, and the test would fail on a type mismatch that has nothing to do
    // with what it was checking.
    private final LinkedBlockingQueue<Envelope> replies = new LinkedBlockingQueue<>();
    private final LinkedBlockingQueue<Envelope> pushes = new LinkedBlockingQueue<>();
    private final CountDownLatch closed = new CountDownLatch(1);
    private long nextCorrelation = 1;

    public TestClient(int port) throws IOException {
        connection = Connection.connect("127.0.0.1", port, new PacketCodec(), 3_000);
        connection.setErrorLog(message -> { });
        connection.setListener(envelope -> {
            if (envelope.isReply()) {
                replies.add(envelope);
            } else {
                pushes.add(envelope);
            }
        });
        connection.setCloseHandler(ignored -> closed.countDown());
        connection.start();
    }

    // Connect and complete the handshake, which every real client does before anything else.
    public static TestClient connected(int port) throws IOException, InterruptedException {
        TestClient client = new TestClient(port);
        HelloResponse hello = client.request(new HelloRequest(Protocol.VERSION, "test"),
                HelloResponse.class);
        assertTrue(hello.accepted(), "handshake refused: " + hello.reason());
        return client;
    }

    public <T extends Packet> T request(Packet request, Class<T> expected) throws InterruptedException {
        Envelope reply = requestRaw(request);
        return assertInstanceOf(expected, reply.payload(),
                "expected " + expected.getSimpleName() + " but got " + reply.type().tag());
    }

    // For the cases where the reply type is the thing under test and asserting it up front would hide
    // the failure behind a ClassCastException.
    public Envelope requestRaw(Packet request) throws InterruptedException {
        long correlation = nextCorrelation++;
        assertTrue(connection.send(request, correlation), "could not send " + request);
        Envelope reply = replies.poll(REPLY_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertNotNull(reply, "no reply to " + request.getClass().getSimpleName()
                + " within " + REPLY_TIMEOUT_MS + "ms");
        assertEquals(correlation, reply.correlationId(), "reply answered the wrong request");
        return reply;
    }

    // Fire and forget, for a request whose interesting consequence is a PUSH rather than a reply --
    // the second player joining the queue, whose real result is a MatchStart on both screens.
    public void send(Packet packet) {
        assertTrue(connection.send(packet), "could not send " + packet);
    }

    // The next unsolicited packet of this type, waiting for it to arrive.
    //
    // Skips pushes of other types rather than failing on them: several can be in flight at once (an
    // OpponentDisconnected immediately followed by a MatchOver), and a test that cared about the
    // second should not break because the first arrived first.
    public <T extends Packet> T awaitPush(Class<T> expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + REPLY_TIMEOUT_MS;
        long remaining;
        while ((remaining = deadline - System.currentTimeMillis()) > 0) {
            Envelope push = pushes.poll(remaining, TimeUnit.MILLISECONDS);
            if (push == null) {
                break;
            }
            if (expected.isInstance(push.payload())) {
                return expected.cast(push.payload());
            }
        }
        throw new AssertionError("no " + expected.getSimpleName() + " pushed within "
                + REPLY_TIMEOUT_MS + "ms");
    }

    // Asserts nothing arrives. Used where the absence IS the behaviour -- a challenge that must not
    // start a match, a queue join that must not pair.
    public boolean noPushWithin(long millis) throws InterruptedException {
        return pushes.poll(millis, TimeUnit.MILLISECONDS) == null;
    }

    public boolean awaitClosed(long timeoutMs) throws InterruptedException {
        return closed.await(timeoutMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() {
        connection.close();
    }
}
