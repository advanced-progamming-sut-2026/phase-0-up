package views.net;

import net.Connection;
import net.Envelope;
import net.Packet;
import net.PacketCodec;
import net.PacketType;
import net.Protocol;
import net.packets.HelloRequest;
import net.packets.HelloResponse;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

// The client's single link to the server.
//
// ## Request/reply on a socket that also pushes
//
// Most of what this game asks the server is a question with an answer: is this username taken, are
// these credentials right, here is my profile. But the same socket also carries things nobody asked
// for -- challenge invites, board snapshots, an opponent's reaction. So "wait for the reply" cannot
// mean "read the next line": very often the next line is not the answer.
//
// Every request therefore carries a correlation id, and the server echoes it. A reply is handed to
// whoever is waiting on that id; anything without one is a push and goes to the push listener.
//
// ## These calls BLOCK, and that is a deliberate trade
//
// request() waits for the answer. On the graphical build that means the render thread waits, because
// the whole command layer is synchronous: LoginCommand asks a question and acts on the answer, and it
// is the SAME LoginCommand the terminal build runs. Making it asynchronous would mean forking the
// commands -- exactly the duplication the Renderers seam exists to prevent, and the reason a rule
// added to a Command can never drift between the two front ends.
//
// What makes the trade acceptable: the connection is opened once at start-up rather than per request,
// so a healthy LAN round trip is a millisecond or two, and every call is bounded by a timeout that
// produces a real error message instead of a frozen window. What makes it acceptable to WRITE: only
// the account operations go through here. Nothing in the game loop does -- match traffic is pushed and
// never waited on.
public final class NetClient implements AutoCloseable {

    // Long enough to survive a hiccup, short enough that a dead server is a message rather than a
    // hang. A blocked render thread past a couple of seconds reads as a crash to the player.
    private static final long REQUEST_TIMEOUT_MS = 5_000;
    private static final int CONNECT_TIMEOUT_MS = 5_000;

    private final PacketCodec codec = new PacketCodec();
    private final AtomicLong correlations = new AtomicLong();

    // A one-slot queue per in-flight request: the reader thread drops the reply in and the waiting
    // caller takes it out. A queue per request rather than a shared lock so two requests in flight at
    // once (the game never does this today, but a background sync alongside a menu action would) cannot
    // collect each other's answers.
    //
    // ## It is a BUFFER, and it has to be -- this was a SynchronousQueue and that is a race
    //
    // A SynchronousQueue has no capacity: offer() succeeds only if a consumer is parked in poll() at
    // that exact instant, and otherwise throws the item away. But the caller cannot be parked yet when
    // it sends -- it has to put the slot in the map, write to the socket, and only then start waiting --
    // so a server that answers inside that window has its reply DROPPED. The caller then waits out the
    // full five seconds and reports "the server did not answer in time" about a server that answered
    // immediately, which is the one thing that had not happened.
    //
    // On a loopback socket with a warm server that window is wide enough to lose a handshake, and the
    // test suite lost one about a third of the time. Over a LAN it is the same race, just rarer.
    // ArrayBlockingQueue(1) holds the reply until the caller gets there and still never blocks the
    // reader thread: offer() returns immediately either way, and a reply to a request that has already
    // timed out finds no slot in the map at all.
    private final Map<Long, ArrayBlockingQueue<Envelope>> pending = new ConcurrentHashMap<>();

    private volatile Connection connection;
    private volatile String host = "localhost";
    private volatile int port = Protocol.DEFAULT_PORT;
    private volatile Consumer<Envelope> pushListener = envelope -> { };
    private volatile Runnable disconnectListener = () -> { };
    private volatile String lastError;

    // -Dpvz.server=host:port, falling back to localhost on the protocol's default port.
    public void configureFromSystemProperties() {
        String raw = System.getProperty(Protocol.SERVER_PROPERTY, "").trim();
        if (raw.isEmpty()) {
            return;
        }
        int colon = raw.lastIndexOf(':');
        if (colon < 0) {
            host = raw;
            return;
        }
        host = raw.substring(0, colon);
        try {
            port = Integer.parseInt(raw.substring(colon + 1).trim());
        } catch (NumberFormatException e) {
            lastError = "-D" + Protocol.SERVER_PROPERTY + "=" + raw + " has no usable port";
        }
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    public String lastError() {
        return lastError;
    }

    // What to do with packets nobody asked for. Set by the lobby and the match screen.
    public void setPushListener(Consumer<Envelope> pushListener) {
        if (pushListener != null) {
            this.pushListener = pushListener;
        }
    }

    public void setDisconnectListener(Runnable disconnectListener) {
        if (disconnectListener != null) {
            this.disconnectListener = disconnectListener;
        }
    }

    public boolean isConnected() {
        Connection current = connection;
        return current != null && current.isOpen();
    }

    // Connect and shake hands. Returns false with lastError() set rather than throwing: the caller is
    // a composition root that has to keep going and show a message, not abort.
    public boolean connect() {
        try {
            Connection link = Connection.connect(host, port, codec, CONNECT_TIMEOUT_MS);
            link.setErrorLog(message -> lastError = message);
            // Installed before start(), or the first packet can arrive with nothing to hand it to.
            link.setListener(this::onPacket);
            link.setCloseHandler(ignored -> onDisconnected());
            link.start();
            this.connection = link;

            HelloResponse hello = request(new HelloRequest(Protocol.VERSION, "pvz2-client"),
                    HelloResponse.class);
            if (hello == null || !hello.accepted()) {
                lastError = hello == null
                        ? "The server did not answer the handshake."
                        : hello.reason();
                link.close();
                return false;
            }
            return true;
        } catch (IOException e) {
            lastError = "Could not reach " + host + ":" + port + " -- " + e.getMessage();
            return false;
        }
    }

    // Ask, and wait. Returns null when the link is down, the server did not answer in time, or it
    // answered with something other than what was asked for -- lastError() says which.
    public <T extends Packet> T request(Packet request, Class<T> expected) {
        Envelope reply = requestRaw(request);
        if (reply == null) {
            return null;
        }
        if (!expected.isInstance(reply.payload())) {
            // Usually the auth gate answering with an AckResponse where the caller expected something
            // richer. Naming both sides makes that immediately readable in a log.
            lastError = "Expected " + expected.getSimpleName() + " but the server sent "
                    + reply.type().tag() + ".";
            return null;
        }
        return expected.cast(reply.payload());
    }

    // The same round trip, handing back the whole envelope.
    //
    // For the requests the server may legitimately answer in more than ONE shape. A challenge comes
    // back as an AckResponse when it was sent and a ChallengeRejected when it was not, and both are
    // replies rather than pushes -- so a caller that named a single expected type got null for the
    // refusal and reported it as "the server isn't answering", which is the one thing that had not
    // happened. Ask for the envelope and decide from what actually arrived.
    public Envelope requestRaw(Packet request) {
        Connection link = connection;
        if (link == null || !link.isOpen()) {
            lastError = "Not connected to the server.";
            return null;
        }
        long correlation = correlations.incrementAndGet();
        ArrayBlockingQueue<Envelope> slot = new ArrayBlockingQueue<>(1);
        pending.put(correlation, slot);
        try {
            if (!link.send(request, correlation)) {
                lastError = "Could not send " + request.getClass().getSimpleName() + ".";
                return null;
            }
            Envelope reply = slot.poll(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (reply == null) {
                lastError = "The server did not answer in time.";
                return null;
            }
            if (reply.payload() == null) {
                // The sentinel onDisconnected pushes to wake abandoned waiters. See below.
                lastError = "Lost the connection to the server.";
                return null;
            }
            return reply;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            lastError = "Interrupted while waiting for the server.";
            return null;
        } finally {
            // Always, including on timeout -- otherwise a slow answer arriving later finds a stale slot
            // and the map grows for the life of the session.
            pending.remove(correlation);
        }
    }

    // Fire and forget, for anything whose answer is a push rather than a reply (in-match commands).
    public boolean send(Packet packet) {
        Connection link = connection;
        return link != null && link.send(packet);
    }

    // Runs on the reader thread. Replies are handed to their waiter; everything else is a push.
    private void onPacket(Envelope envelope) {
        if (envelope.isReply()) {
            ArrayBlockingQueue<Envelope> slot = pending.get(envelope.correlationId());
            // offer, not put: the slot holds one reply and there is only ever one, but blocking here on
            // any anomaly would wedge the reader thread -- and with it every push -- for the session.
            // The slot buffers, so this succeeds whether or not the caller has reached poll() yet.
            if (slot != null && slot.offer(envelope)) {
                return;
            }
            // A late answer to an abandoned request. Dropped on purpose -- but a HELLO arriving here
            // would mean the handshake itself timed out, which is worth knowing about.
            if (envelope.type() == PacketType.HELLO_RES) {
                lastError = "The handshake answer arrived too late.";
            }
            return;
        }
        pushListener.accept(envelope);
    }

    private void onDisconnected() {
        // Wake every waiting caller rather than leaving them to time out one at a time. Without this a
        // server that dies mid-session freezes the render thread for the full timeout on every
        // subsequent menu action -- five seconds per click, which reads as the game having hung.
        //
        // A null payload is the sentinel for "the link went away", and request() reports it as exactly
        // that. It cannot be confused with a real packet: PacketCodec refuses to decode one without a
        // payload, so nothing else can ever produce this shape.
        for (ArrayBlockingQueue<Envelope> slot : pending.values()) {
            slot.offer(new Envelope(PacketType.ACK, Envelope.NO_CORRELATION, null));
        }
        pending.clear();
        disconnectListener.run();
    }

    @Override
    public void close() {
        Connection link = connection;
        if (link != null) {
            link.closeAfterFlush();
        }
        connection = null;
    }
}
