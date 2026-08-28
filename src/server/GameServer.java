package server;

import models.user.User;
import net.Connection;
import net.Envelope;
import net.PacketCodec;
import net.PacketType;
import net.Protocol;
import net.packets.AckResponse;
import net.packets.HelloRequest;
import net.packets.HelloResponse;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

// The listening socket, the connected clients, and where each packet goes.
//
// Deliberately holds no game rules and no account rules. It accepts sockets, decides whether a packet
// is allowed to be sent at all, and hands it to whoever registered for that type. Accounts (T3.3), the
// lobby (T3.5) and match traffic (T3.7) each register their own handlers, and none of them has to know
// the others exist.
//
// ## Presence is not a separate table
//
// `online` IS the set of authenticated sessions. There is no second "who is logged in" structure to
// keep in step with reality, which is what makes the lobby's online list and the challenge router
// incapable of disagreeing about whether somebody is reachable: the same map that routes a challenge
// is the one that answered "they are online".
public final class GameServer implements AutoCloseable {

    // How a registration is stored. A record rather than two parallel maps, so a type cannot end up
    // with a handler and no auth level.
    private record Registration(AuthLevel level, PacketHandler handler) { }

    private final int requestedPort;
    private final PacketCodec codec = new PacketCodec();

    private final Map<PacketType, Registration> handlers = new EnumMap<>(PacketType.class);

    // Every live connection, authenticated or not. Needed separately from `online` so shutdown can
    // close the ones still sitting at the login screen.
    private final Set<ClientSession> sessions = ConcurrentHashMap.newKeySet();

    // Signed-in players, keyed by LOWER-CASED username.
    //
    // Lower-cased because DatabaseManager identifies accounts case-insensitively -- "Amir" and "amir"
    // are the same gardener there. If presence were keyed on the typed casing, challenging "Amir" would
    // report a player who is demonstrably online as offline, and the two halves of the system would
    // disagree about who exists.
    private final Map<String, ClientSession> online = new ConcurrentHashMap<>();

    private final AtomicBoolean running = new AtomicBoolean();
    private ServerSocket serverSocket;
    private Thread acceptThread;

    private Consumer<String> log = message -> System.out.println("[server] " + message);
    private Consumer<String> errorLog = message -> System.err.println("[server] " + message);

    public GameServer(int port) {
        this.requestedPort = port;
    }

    public void setLog(Consumer<String> log) {
        if (log != null) {
            this.log = log;
        }
    }

    public void setErrorLog(Consumer<String> errorLog) {
        if (errorLog != null) {
            this.errorLog = errorLog;
        }
    }

    // For the feature services, so a match or an account handler reports through the same sink the
    // server itself does instead of printing straight to stdout -- which is what makes the log
    // redirectable in a test.
    public void log(String message) {
        log.accept(message);
    }

    // ---- wiring ---------------------------------------------------------------------------------

    // Refuses a second handler for the same type rather than silently replacing the first, which would
    // leave a feature area wired up, compiling, and doing nothing.
    public void register(PacketType type, AuthLevel level, PacketHandler handler) {
        if (type == null || level == null || handler == null) {
            throw new IllegalArgumentException("packet type, auth level and handler are all required");
        }
        Registration clash = handlers.putIfAbsent(type, new Registration(level, handler));
        if (clash != null) {
            throw new IllegalStateException(type + " already has a handler registered");
        }
    }

    // ---- lifecycle ------------------------------------------------------------------------------

    // Binds and starts accepting. Port 0 asks the OS for a free one -- which is how the tests run
    // without either picking a port that might be busy on a teammate's machine or serialising every
    // test that needs a server onto one fixed number.
    public void start() throws IOException {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("already started");
        }
        serverSocket = new ServerSocket(requestedPort);
        acceptThread = new Thread(this::acceptLoop, "server-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
        log.accept("listening on port " + port());
    }

    // The port actually bound, which is not necessarily the one asked for: with 0 the OS picks, and a
    // test has no other way to learn where to connect.
    public int port() {
        ServerSocket socket = serverSocket;
        return socket == null ? -1 : socket.getLocalPort();
    }

    public boolean isRunning() {
        return running.get();
    }

    private void acceptLoop() {
        while (running.get()) {
            Socket socket;
            try {
                socket = serverSocket.accept();
            } catch (IOException e) {
                if (running.get()) {
                    errorLog.accept("accept failed: " + e.getMessage());
                }
                // close() closes the ServerSocket, which is what unblocks accept() -- so an exception
                // here during shutdown is the expected exit, not a fault.
                break;
            }
            try {
                openSession(socket);
            } catch (IOException e) {
                // One client that dies during the handshake must not take the accept loop with it, or
                // a single bad connection ends the server for everybody.
                errorLog.accept("could not open a session: " + e.getMessage());
                closeQuietly(socket);
            }
        }
        running.set(false);
    }

    private void openSession(Socket socket) throws IOException {
        Connection connection = Connection.accept(socket, codec);
        ClientSession session = new ClientSession(connection);
        connection.setErrorLog(errorLog);
        // The listener is installed BEFORE start(), or the first packet can arrive with nothing to
        // hand it to -- see Connection.start.
        connection.setListener(envelope -> dispatch(session, envelope));
        connection.setCloseHandler(ignored -> dropSession(session));
        sessions.add(session);
        connection.start();
        log.accept("connected: " + connection.remoteAddress());
    }

    @Override
    public void close() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        // Closing the ServerSocket is what unblocks the accept() the loop is parked in.
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
            // Shutting down anyway.
        }
        // A copy, because each close() calls back into dropSession, which mutates this set.
        for (ClientSession session : new ArrayList<>(sessions)) {
            session.close();
        }
        // Then WAIT for the disconnect handlers.
        //
        // A dropped socket runs its listeners on that connection's reader thread, and those listeners
        // do real work: forfeiting a match, writing both players' records to disk. Returning from
        // close() while one is halfway through means the process can exit -- or a test's temp
        // directory be deleted -- underneath a half-written roster file.
        awaitSessionsDrained();
        sessions.clear();
        online.clear();
        log.accept("stopped");
    }

    // Every socket has either been listed and not yet dropped, or is being dropped right now. Bounded,
    // because a listener that hangs must not stop the server from shutting down at all -- a two-second
    // wait is generous for a file write and short enough that nobody sits watching a stuck process.
    private void awaitSessionsDrained() {
        long deadline = System.currentTimeMillis() + 2_000;
        while (System.currentTimeMillis() < deadline) {
            if (sessions.isEmpty() && dropsInFlight.get() == 0) {
                return;
            }
            try {
                Thread.sleep(5);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        errorLog.accept("gave up waiting for " + sessions.size() + " session(s) to finish closing");
    }

    // ---- dispatch -------------------------------------------------------------------------------

    // Every inbound packet passes through here, on the sending client's reader thread.
    private void dispatch(ClientSession session, Envelope envelope) {
        // The handshake is answered here rather than through a registered handler, because it is the
        // one exchange that has to work before anything else is wired up -- including on a server whose
        // account and match handlers have not been registered at all.
        if (envelope.type() == PacketType.HELLO_REQ) {
            handleHello(session, envelope);
            return;
        }
        Registration registration = handlers.get(envelope.type());
        if (registration == null) {
            // Fails CLOSED. An unregistered type is refused with an explanation rather than ignored,
            // so a feature that was never wired up says so instead of looking like a hung request.
            session.reply(envelope, new AckResponse(false,
                    envelope.type().tag() + " is not something this server handles."));
            errorLog.accept(session + " sent unhandled " + envelope.type().tag());
            return;
        }
        if (registration.level() == AuthLevel.AUTHENTICATED && !session.isAuthenticated()) {
            session.reply(envelope, new AckResponse(false, "Log in first -- the lawn is members only."));
            return;
        }
        registration.handler().handle(session, envelope);
    }

    // Version before identity. A build mismatch reported here is a build mismatch; the same mismatch
    // discovered later surfaces as an unknown tag mid-match, which tells nobody anything.
    private void handleHello(ClientSession session, Envelope envelope) {
        HelloRequest hello = envelope.as(HelloRequest.class);
        if (hello.protocolVersion() != Protocol.VERSION) {
            session.reply(envelope, new HelloResponse(false, Protocol.VERSION,
                    "This server speaks protocol v" + Protocol.VERSION + " and you speak v"
                            + hello.protocolVersion() + ". Someone needs an update."));
            // Refused, and closed -- but not before the refusal has actually gone out. A plain close()
            // here would discard the queued HelloResponse and the client would see nothing but a
            // dropped socket, which is precisely the diagnosis this exchange exists to prevent.
            session.closeAfterFlush();
            return;
        }
        session.reply(envelope, new HelloResponse(true, Protocol.VERSION, null));
    }

    // ---- presence -------------------------------------------------------------------------------

    // Bind a signed-in account to a connection. The ONLY way a session becomes authenticated -- which
    // is why ClientSession.authenticateAs is package-private and this is not: authenticating without
    // registering presence would produce a player who is logged in and unreachable, and the challenge
    // router would report them offline.
    //
    // Returns the session that was displaced, if the same account was already signed in somewhere else.
    // Signing in on a second device is expected (the spec requires progress to follow the account, not
    // the machine), so the newer session wins and the older is told why rather than both running and
    // racing each other's profile writes.
    public ClientSession attachAuthenticated(ClientSession session, User user) {
        if (session == null || user == null || user.getUsername() == null) {
            throw new IllegalArgumentException("a session and a named user are both required");
        }
        session.authenticateAs(user);
        ClientSession displaced = online.put(key(user.getUsername()), session);
        if (displaced != null && displaced != session) {
            displaced.send(new AckResponse(false,
                    "You just signed in somewhere else, so this lawn is closing."));
            displaced.clearAuthentication();
            // After the flush, or the player on the older device is disconnected with no explanation
            // at all -- which reads as a crash rather than as the deliberate handover it is.
            displaced.closeAfterFlush();
            log.accept("displaced an older session for " + user.getUsername());
        }
        log.accept("signed in: " + user.getUsername());
        return displaced;
    }

    // Signing out without dropping the socket -- the player is still connected, just anonymous again.
    public void detachAuthenticated(ClientSession session) {
        User user = session.user();
        if (user != null && user.getUsername() != null) {
            // remove(key, value), not remove(key): if this account has ALREADY been displaced by a
            // newer session, the map entry belongs to that newer one and removing it blindly would
            // sign out the player who is actually here.
            online.remove(key(user.getUsername()), session);
        }
        session.clearAuthentication();
    }

    // Told when any session's socket goes away, for any reason.
    //
    // Matchmaking needs this: a dropped player has to be pulled out of the queue, have their
    // invitations withdrawn, and forfeit whatever match they were in. Without it the lobby silently
    // fills with ghosts -- challenges nobody can answer and queue entries that pair with no one.
    //
    // A list rather than a single listener, because more than one feature area will want it (T3.7's
    // match runner has to stop ticking a board whose player has gone).
    private final List<Consumer<ClientSession>> sessionClosedListeners = new java.util.ArrayList<>();

    public void addSessionClosedListener(Consumer<ClientSession> listener) {
        if (listener != null) {
            sessionClosedListeners.add(listener);
        }
    }

    // How many disconnects are being processed right now. Incremented BEFORE the session leaves the
    // set, which is what makes close()'s wait airtight: a session is either still listed or still
    // counted here, never neither, from the moment its socket drops until its listeners have finished.
    private final java.util.concurrent.atomic.AtomicInteger dropsInFlight =
            new java.util.concurrent.atomic.AtomicInteger();

    private void dropSession(ClientSession session) {
        dropsInFlight.incrementAndGet();
        try {
            runDrop(session);
        } finally {
            dropsInFlight.decrementAndGet();
        }
    }

    private void runDrop(ClientSession session) {
        sessions.remove(session);
        // BEFORE detaching: the listeners identify the player by their account, and a session that has
        // already been made anonymous cannot be matched to the queue entry or match it belongs to.
        for (Consumer<ClientSession> listener : sessionClosedListeners) {
            try {
                listener.accept(session);
            } catch (RuntimeException e) {
                // One listener throwing must not stop the others, or a player stays in the queue
                // forever because an unrelated feature had a bad day.
                errorLog.accept("session-closed listener threw for " + session + ": " + e);
            }
        }
        detachAuthenticated(session);
        log.accept("disconnected: " + session);
    }

    // Who is signed in right now, in display casing. This is the lobby's list and the challenge
    // router's address book, and it is a live view of the connection map rather than a cache.
    public List<String> onlineUsernames() {
        List<String> names = new ArrayList<>(online.size());
        for (ClientSession session : online.values()) {
            User user = session.user();
            if (user != null && user.getUsername() != null) {
                names.add(user.getUsername());
            }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    // The session a username is signed in on, or null when nobody is. Case-insensitive, for the same
    // reason the map is keyed that way.
    public ClientSession sessionOf(String username) {
        return username == null ? null : online.get(key(username));
    }

    public boolean isOnline(String username) {
        return sessionOf(username) != null;
    }

    public int connectionCount() {
        return sessions.size();
    }

    public int onlineCount() {
        return online.size();
    }

    private static String key(String username) {
        return username.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // Nothing useful left to do with a socket that is already broken.
        }
    }
}
