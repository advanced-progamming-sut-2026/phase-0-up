package net;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

// One TCP link, in both directions, with a thread each way.
//
// ## Why two threads and a queue, rather than writing straight from the caller
//
// A TCP write blocks when the peer stops reading -- a client the OS has paused, one whose window is
// being dragged, or one that has gone away without closing. If a match's tick thread wrote directly to
// both clients, ONE slow client would stall the simulation for BOTH. So sending is a queue push (never
// blocks) and a dedicated writer thread does the blocking part. The worst a dead peer can do is fill
// its own queue.
//
// The reader thread exists for the mirror-image reason: readLine() blocks, and nothing in this game
// has a spare thread to lose. Note that the listener therefore runs on the READER thread -- see
// PacketListener for what each side must do about that.
//
// ## Framing
//
// One packet per line. That is why PacketCodec must not pretty-print: a newline inside a packet would
// split it in two. A BLANK line is a heartbeat and is skipped -- it costs no packet type and proves an
// idle-but-healthy connection is still there, which a read timeout alone cannot distinguish from a
// dead one.
public final class Connection implements AutoCloseable {

    private static final String HEARTBEAT_LINE = "";
    // Deep enough to absorb a stalled peer for several seconds of snapshots, shallow enough that a
    // genuinely dead one is noticed rather than buffering megabytes. ~25 s at 10 Hz.
    private static final int SEND_QUEUE_CAPACITY = 256;
    // Pushed to wake the writer thread on close. Compared by IDENTITY, so it can never collide with a
    // real line no matter what a packet happens to serialise to -- which is also why it is built with
    // an explicit constructor rather than written as a literal the JVM would intern.
    @SuppressWarnings("StringOperationCanBeSimplified")
    private static final String POISON = new String("__close__");

    private static final AtomicLong THREAD_SEQ = new AtomicLong();

    private final Socket socket;
    private final PacketCodec codec;
    private final BlockingQueue<String> outbound = new LinkedBlockingQueue<>(SEND_QUEUE_CAPACITY);
    private final AtomicBoolean closed = new AtomicBoolean();
    // Set by closeAfterFlush: the link is on its way out, but the writer is still draining. New sends
    // are refused from this moment, so nothing can be queued behind the goodbye and lost anyway.
    private final AtomicBoolean flushing = new AtomicBoolean();
    private final String label;

    private volatile PacketListener listener;
    // Called once when the link goes down, for any reason. The server uses it to drop the session and
    // forfeit a match; the client uses it to fall back to the "server unreachable" screen.
    private volatile Consumer<Connection> closeHandler;
    private volatile Consumer<String> errorLog = message -> System.err.println("[net] " + message);

    private Thread readerThread;
    private Thread writerThread;

    private Connection(Socket socket, PacketCodec codec, String label) throws IOException {
        this.socket = socket;
        this.codec = codec;
        this.label = label;
        // Without this, Nagle's algorithm holds a small write back waiting for more to coalesce with.
        // On a 100 ms tick that is up to 40 ms of invented latency on every command and every
        // snapshot, and it looks exactly like a slow network.
        socket.setTcpNoDelay(true);
        // A peer that has said nothing at all for this long is gone. The heartbeat is what makes that
        // safe to assume: a healthy idle connection still writes a blank line every few seconds.
        socket.setSoTimeout(Protocol.READ_TIMEOUT_MS);
    }

    public static Connection accept(Socket accepted, PacketCodec codec) throws IOException {
        return new Connection(accepted, codec, "in:" + accepted.getRemoteSocketAddress());
    }

    public static Connection connect(String host, int port, PacketCodec codec, int timeoutMs)
            throws IOException {
        Socket socket = new Socket();
        // Connect with an explicit timeout: the Socket(host, port) constructor waits for the OS
        // default instead, which on an unreachable host is over a minute of apparently frozen game.
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        return new Connection(socket, codec, "out:" + host + ":" + port);
    }

    public void setListener(PacketListener listener) {
        this.listener = listener;
    }

    public void setCloseHandler(Consumer<Connection> closeHandler) {
        this.closeHandler = closeHandler;
    }

    public void setErrorLog(Consumer<String> errorLog) {
        if (errorLog != null) {
            this.errorLog = errorLog;
        }
    }

    public boolean isOpen() {
        return !closed.get() && !socket.isClosed();
    }

    public String remoteAddress() {
        return String.valueOf(socket.getRemoteSocketAddress());
    }

    // Starts both threads. Separate from construction so a caller can install its listener first --
    // otherwise the very first packet can arrive before there is anything to hand it to, a race that
    // only shows up under load.
    public void start() {
        long seq = THREAD_SEQ.incrementAndGet();
        readerThread = new Thread(this::readLoop, "net-read-" + seq + "-" + label);
        writerThread = new Thread(this::writeLoop, "net-write-" + seq + "-" + label);
        readerThread.setDaemon(true);
        writerThread.setDaemon(true);
        readerThread.start();
        writerThread.start();
    }

    public boolean send(Packet packet) {
        return send(packet, Envelope.NO_CORRELATION);
    }

    // Never blocks. Returns false when the packet was dropped -- the link is closed, or the peer is so
    // far behind that its queue is full. A dropped SNAPSHOT is harmless (another follows in 100 ms);
    // a dropped reply is not, so callers that need one check this.
    public boolean send(Packet packet, long correlationId) {
        if (!isOpen() || flushing.get()) {
            return false;
        }
        String line;
        try {
            line = codec.encode(packet, correlationId);
        } catch (RuntimeException e) {
            errorLog.accept("could not encode " + packet.getClass().getSimpleName() + ": "
                    + e.getMessage());
            return false;
        }
        if (!outbound.offer(line)) {
            errorLog.accept(label + " send queue is full; dropping "
                    + packet.getClass().getSimpleName());
            return false;
        }
        return true;
    }

    private void readLoop() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while (isOpen() && (line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;   // heartbeat
                }
                dispatch(line);
            }
        } catch (SocketTimeoutException e) {
            errorLog.accept(label + " went silent for " + Protocol.READ_TIMEOUT_MS + "ms");
        } catch (IOException e) {
            if (isOpen()) {
                errorLog.accept(label + " read failed: " + e.getMessage());
            }
        } finally {
            close();
        }
    }

    // A malformed line does NOT kill the connection. An unknown tag usually means the peer is a newer
    // build sending something this one has not heard of, and dropping a whole session over one
    // skippable line would turn a harmless version skew into a disconnect mid-match.
    private void dispatch(String line) {
        Envelope envelope;
        try {
            envelope = codec.decode(line);
        } catch (ProtocolException e) {
            errorLog.accept(label + " sent an undecodable packet: " + e.getMessage());
            return;
        }
        PacketListener current = listener;
        if (current == null) {
            errorLog.accept(label + " sent " + envelope.type().tag() + " before a listener existed");
            return;
        }
        try {
            current.onPacket(envelope);
        } catch (RuntimeException e) {
            // A handler that throws must not take the reader thread with it -- that would silently
            // stop delivering EVERY later packet on this connection, with the game still on screen.
            errorLog.accept(label + " handler threw on " + envelope.type().tag() + ": " + e);
        }
    }

    private void writeLoop() {
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
            while (isOpen()) {
                // Poll rather than take: when nothing is queued this wakes on its own and sends a
                // heartbeat, which is what keeps an idle lobby connection from tripping the read
                // timeout at the other end.
                String line = outbound.poll(Protocol.HEARTBEAT_MS, TimeUnit.MILLISECONDS);
                if (line == POISON) {
                    break;
                }
                writer.write(line == null ? HEARTBEAT_LINE : line);
                writer.write('\n');
                // Flushed every packet, deliberately. A BufferedWriter that never flushes holds a
                // command or a snapshot back for an unbounded time, which defeats the whole point of
                // the TCP_NODELAY above.
                writer.flush();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            if (isOpen()) {
                errorLog.accept(label + " write failed: " + e.getMessage());
            }
        } finally {
            close();
        }
    }

    // Say goodbye, THEN hang up.
    //
    // send() only queues; the writer thread does the actual writing. So closing immediately after a
    // send throws away the very packet that explains the closure, and the peer sees an unexplained
    // dropped connection instead of the refusal that was written for them. That is not hypothetical --
    // it is exactly how a protocol-version mismatch and a displaced sign-in both failed silently, and
    // in both cases the whole point of the packet was to say why.
    //
    // Waits for the writer to drain the queue and exit, bounded by timeoutMs, then closes for real.
    // Safe to call from the reader thread or from a handler; not from the writer thread itself, which
    // would be joining itself -- the writer's own exit path goes through close() instead.
    public void closeAfterFlush(long timeoutMs) {
        if (closed.get() || !flushing.compareAndSet(false, true)) {
            return;
        }
        // Queued at the TAIL, so everything already waiting is written before the writer sees it.
        outbound.offer(POISON);
        Thread writer = writerThread;
        if (writer != null && writer != Thread.currentThread()) {
            try {
                writer.join(Math.max(1L, timeoutMs));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        close();
    }

    public void closeAfterFlush() {
        closeAfterFlush(Protocol.CLOSE_FLUSH_MS);
    }

    // Idempotent, and safe to call from either thread or from a handler. Both loops call it in their
    // finally blocks, so whichever side notices the break first tears the whole link down.
    //
    // Immediate: anything still queued is discarded. Use closeAfterFlush when the last packet sent is
    // the reason for closing.
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        outbound.offer(POISON);
        try {
            socket.close();
        } catch (IOException ignored) {
            // Already gone; nothing useful left to do or report.
        }
        Consumer<Connection> handler = closeHandler;
        if (handler != null) {
            try {
                handler.accept(this);
            } catch (RuntimeException e) {
                errorLog.accept(label + " close handler threw: " + e);
            }
        }
    }
}
