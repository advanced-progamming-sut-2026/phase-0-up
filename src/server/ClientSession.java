package server;

import models.user.User;
import net.Connection;
import net.Envelope;
import net.Packet;

import java.util.concurrent.atomic.AtomicLong;

// One connected client, for as long as its socket lasts.
//
// Holds the three things every handler needs to know about whoever just sent a packet: the link to
// write back down, who they are once they have proved it, and which match (if any) they are playing.
// It deliberately holds no game rules -- it is the identity of a caller, not an actor.
//
// ## Why the User reference and not just a username
//
// The account object IS the live one out of the server's roster, the same instance the leaderboard and
// every profile sync read. Holding a copy would mean two versions of a player's coins existing at once
// and no rule about which wins.
public final class ClientSession implements AutoCloseable {

    private static final AtomicLong SEQ = new AtomicLong();

    private final long id = SEQ.incrementAndGet();
    private final Connection connection;

    // Written by the account handlers on the reader thread, read by the lobby and by match threads.
    // Volatile rather than synchronized: it is a single reference, written rarely and read often, and
    // no reader needs it to be consistent with anything else at the same instant.
    private volatile User user;

    // What this client is currently playing, or null in the menus.
    //
    // Read by the lobby to answer "are they available?" and by the disconnect path to know what to
    // forfeit. Volatile for the same reason `user` is: a single reference, written rarely, read from
    // several threads, and never needing to be consistent with anything else at the same instant.
    private volatile server.match.Match match;

    ClientSession(Connection connection) {
        this.connection = connection;
    }

    public long id() {
        return id;
    }

    public Connection connection() {
        return connection;
    }

    public User user() {
        return user;
    }

    public boolean isAuthenticated() {
        return user != null;
    }

    // The name to log and to show other players. Never blows up on an unauthenticated session, because
    // it is used in exactly the log lines that describe one.
    public String username() {
        User current = user;
        return current == null ? "<anonymous>" : current.getUsername();
    }

    // Handed out at sign-in and returned in the LoginResponse.
    //
    // Not used for authentication today -- a client proves who it is with its password hash. It exists
    // for match reconnection (T3.7): a player whose socket drops mid-match has a grace period to come
    // back, and the returning connection needs to prove it is the same player without a fresh sign-in.
    private volatile String token;

    void authenticateAs(User user) {
        this.user = user;
    }

    void clearAuthentication() {
        this.user = null;
        this.token = null;
    }

    public String token() {
        return token;
    }

    // Returns the token so the caller can put it straight into the reply.
    public String issueToken(String token) {
        this.token = token;
        return token;
    }

    public server.match.Match match() {
        return match;
    }

    public void setMatch(server.match.Match match) {
        this.match = match;
    }

    public boolean send(Packet packet) {
        return connection.send(packet);
    }

    // Answering a request, rather than pushing something unasked.
    //
    // The correlation id is copied off the request, and that is the whole point: a client can have a
    // blocking call in flight while snapshots, challenge invites and reaction relays are arriving down
    // the same socket. Without the echo it has no way to tell which line is its answer, and the reply
    // would be delivered as an unsolicited push to nobody.
    public boolean reply(Envelope request, Packet response) {
        return connection.send(response, request.correlationId());
    }

    @Override
    public void close() {
        connection.close();
    }

    // Closing on purpose, after telling them why -- a refused handshake, a displaced sign-in, the end
    // of a match. The plain close() drops whatever is still queued, which would discard the very
    // packet that explains the closure and leave the player staring at a dead connection.
    public void closeAfterFlush() {
        connection.closeAfterFlush();
    }

    @Override
    public String toString() {
        return "session#" + id + "(" + username() + " @ " + connection.remoteAddress() + ")";
    }
}
