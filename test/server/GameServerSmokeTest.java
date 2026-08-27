package server;

import models.user.Gender;
import models.user.User;
import net.PacketType;
import net.Protocol;
import net.packets.AckResponse;
import net.packets.HelloRequest;
import net.packets.HelloResponse;
import net.packets.LoginRequest;
import net.packets.LoginResponse;
import net.packets.OnlineUsersRequest;
import net.packets.OnlineUsersResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The server actually serving: a real socket, a real client, real threads.
//
// Bound to port 0 -- the OS picks a free one -- so this never collides with a server somebody has left
// running, and several of these can run at once. Hard-coding 7777 would make the suite fail for
// reasons that have nothing to do with the code.
//
// In package `server` on purpose: the presence tests need to reach ClientSession, whose constructor is
// package-private precisely so that nothing outside the server can invent one.
class GameServerSmokeTest {

    private static final long REPLY_TIMEOUT_MS = TestClient.REPLY_TIMEOUT_MS;

    private GameServer server;
    private final List<TestClient> clients = new ArrayList<>();

    @BeforeEach
    void startServer() throws IOException {
        server = new GameServer(0);
        // Quiet: the server narrates every connection, and eleven tests' worth of that buries the one
        // line that matters when something fails.
        server.setLog(message -> { });
        server.setErrorLog(message -> { });
        server.start();
    }

    @AfterEach
    void stopServer() {
        for (TestClient client : clients) {
            client.close();
        }
        clients.clear();
        if (server != null) {
            server.close();
        }
    }

    // ---- handshake ------------------------------------------------------------------------------

    @Test
    @DisplayName("a client connects, says hello, and is accepted")
    void helloIsAccepted() throws Exception {
        TestClient client = connect();
        HelloResponse hello = client.request(new HelloRequest(Protocol.VERSION, "test"),
                HelloResponse.class);

        assertTrue(hello.accepted(), "a matching protocol version must be accepted");
        assertEquals(Protocol.VERSION, hello.serverVersion());
        assertNull(hello.reason());
    }

    @Test
    @DisplayName("a mismatched protocol version is refused, and the link is dropped")
    void versionMismatchIsRefusedAndClosed() throws Exception {
        TestClient client = connect();
        HelloResponse hello = client.request(new HelloRequest(Protocol.VERSION + 99, "from-the-future"),
                HelloResponse.class);

        assertFalse(hello.accepted());
        assertNotNull(hello.reason(), "a refusal has to say why, or nobody can act on it");
        assertTrue(hello.reason().contains("v" + Protocol.VERSION),
                "the message must name both versions: " + hello.reason());

        // And the server hangs up rather than leaving a peer it cannot talk to connected. Deferring
        // the failure only makes it surface later as an unexplained unknown-tag error.
        assertTrue(client.awaitClosed(REPLY_TIMEOUT_MS),
                "the server should close a connection it has just refused");
    }

    // ---- dispatch fails closed ------------------------------------------------------------------

    @Test
    @DisplayName("a packet with no handler is refused with an explanation, not ignored")
    void unhandledPacketIsRefused() throws Exception {
        TestClient client = connect();
        client.request(new HelloRequest(Protocol.VERSION, "test"), HelloResponse.class);

        // Nothing has registered for this: T3.4 will. Until then it must say so -- an ignored request
        // is indistinguishable from a hung one, and a client waiting on a reply that never comes looks
        // like a network fault.
        AckResponse ack = client.request(new OnlineUsersRequest(), AckResponse.class);
        assertFalse(ack.ok());
        assertTrue(ack.message().contains(PacketType.ONLINE_USERS_REQ.tag()),
                "the refusal should name the packet: " + ack.message());
    }

    @Test
    @DisplayName("an authenticated-only packet is refused before sign-in")
    void authGateRefusesAnonymousCallers() throws Exception {
        // Registered but gated. The gate lives in GameServer, not in the handler, which is what lets
        // every handler read session.user() without a null check.
        boolean[] handlerRan = {false};
        server.register(PacketType.ONLINE_USERS_REQ, AuthLevel.AUTHENTICATED,
                (session, envelope) -> {
                    handlerRan[0] = true;
                    session.reply(envelope, new OnlineUsersResponse(server.onlineUsernames()));
                });

        TestClient client = connect();
        client.request(new HelloRequest(Protocol.VERSION, "test"), HelloResponse.class);

        AckResponse ack = client.request(new OnlineUsersRequest(), AckResponse.class);
        assertFalse(ack.ok());
        assertFalse(handlerRan[0], "the handler must not run at all for an unauthenticated caller");
    }

    @Test
    @DisplayName("registering two handlers for one packet type fails loudly")
    void duplicateRegistrationIsRefused() {
        PacketHandler noop = (session, envelope) -> { };
        server.register(PacketType.ONLINE_USERS_REQ, AuthLevel.AUTHENTICATED, noop);

        // Silently replacing the first would leave a feature area wired up, compiling, and dead.
        assertThrows(IllegalStateException.class,
                () -> server.register(PacketType.ONLINE_USERS_REQ, AuthLevel.AUTHENTICATED, noop));
    }

    // ---- presence -------------------------------------------------------------------------------

    @Test
    @DisplayName("signing in puts a player on the online list, and signing out takes them off")
    void presenceFollowsAuthentication() throws Exception {
        registerFakeLogin();
        TestClient client = connect();
        client.request(new HelloRequest(Protocol.VERSION, "test"), HelloResponse.class);

        assertEquals(0, server.onlineCount());

        LoginResponse login = client.request(new LoginRequest("Amir", "hash", false),
                LoginResponse.class);
        assertTrue(login.ok());

        assertEquals(1, server.onlineCount());
        assertEquals(List.of("Amir"), server.onlineUsernames());
        assertTrue(server.isOnline("Amir"));

        client.close();
        // The socket closing is what removes them: presence is the connection map, so there is no
        // second structure that could be left saying they are still here.
        assertTrue(awaitOnlineCount(0), "a dropped connection must leave the online list");
    }

    @Test
    @DisplayName("the online list is case-insensitive, like the account roster it mirrors")
    void presenceIgnoresCase() throws Exception {
        registerFakeLogin();
        TestClient client = connect();
        client.request(new HelloRequest(Protocol.VERSION, "test"), HelloResponse.class);
        client.request(new LoginRequest("Amir", "hash", false), LoginResponse.class);

        // DatabaseManager treats "Amir" and "amir" as the same gardener. If presence disagreed,
        // challenging a player by the casing they did not type would report them offline while they
        // are demonstrably online.
        assertTrue(server.isOnline("amir"));
        assertTrue(server.isOnline("AMIR"));
        assertTrue(server.isOnline("  Amir  "));
        assertNotNull(server.sessionOf("aMiR"));

        // The DISPLAY casing is what the lobby shows, though -- the lower-casing is only the key.
        assertEquals(List.of("Amir"), server.onlineUsernames());
    }

    @Test
    @DisplayName("signing in on a second device displaces the first session")
    void secondSignInDisplacesTheFirst() throws Exception {
        registerFakeLogin();

        TestClient first = connect();
        first.request(new HelloRequest(Protocol.VERSION, "test"), HelloResponse.class);
        first.request(new LoginRequest("Amir", "hash", false), LoginResponse.class);

        TestClient second = connect();
        second.request(new HelloRequest(Protocol.VERSION, "test"), HelloResponse.class);
        second.request(new LoginRequest("Amir", "hash", false), LoginResponse.class);

        // The account is expected to follow the player between devices, so the newer session wins --
        // but the older one is told why and closed, rather than both staying live and racing each
        // other's profile writes.
        assertTrue(first.awaitClosed(REPLY_TIMEOUT_MS), "the older session should be closed");
        assertTrue(awaitOnlineCount(1), "the account should be online exactly once");
        assertTrue(server.isOnline("Amir"));
    }

    @Test
    @DisplayName("a displaced session dropping does not sign out the player who replaced it")
    void displacedSessionDoesNotEvictItsReplacement() throws Exception {
        registerFakeLogin();

        TestClient first = connect();
        first.request(new HelloRequest(Protocol.VERSION, "test"), HelloResponse.class);
        first.request(new LoginRequest("Amir", "hash", false), LoginResponse.class);

        TestClient second = connect();
        second.request(new HelloRequest(Protocol.VERSION, "test"), HelloResponse.class);
        second.request(new LoginRequest("Amir", "hash", false), LoginResponse.class);

        assertTrue(first.awaitClosed(REPLY_TIMEOUT_MS));
        // The displaced connection's close handler runs AFTER the newer one owns the map entry. A
        // blind remove(key) there would sign out the player who is actually connected -- which is why
        // detachAuthenticated uses the two-argument remove(key, value).
        Thread.sleep(150);
        assertEquals(1, server.onlineCount(), "the surviving session must still be signed in");
        assertNotNull(server.sessionOf("Amir"));
    }

    // ---- shutdown -------------------------------------------------------------------------------

    @Test
    @DisplayName("closing the server drops every connection, signed in or not")
    void shutdownClosesEverything() throws Exception {
        registerFakeLogin();

        TestClient signedIn = connect();
        signedIn.request(new HelloRequest(Protocol.VERSION, "test"), HelloResponse.class);
        signedIn.request(new LoginRequest("Amir", "hash", false), LoginResponse.class);

        TestClient anonymous = connect();
        anonymous.request(new HelloRequest(Protocol.VERSION, "test"), HelloResponse.class);

        assertEquals(2, server.connectionCount());

        server.close();

        assertFalse(server.isRunning());
        assertEquals(0, server.onlineCount());
        assertTrue(signedIn.awaitClosed(REPLY_TIMEOUT_MS));
        assertTrue(anonymous.awaitClosed(REPLY_TIMEOUT_MS), "an anonymous session must be closed too");
    }

    @Test
    @DisplayName("port 0 binds a real, discoverable port")
    void ephemeralPortIsReported() {
        assertTrue(server.port() > 0, "port() must report what was actually bound, not the 0 asked for");
    }

    // ---- helpers --------------------------------------------------------------------------------

    // Stands in for the T3.3 account handler: it authenticates whoever asks, with no password check.
    // Enough to exercise the presence invariant, which is what this file is about -- the real
    // credential rules are AccountService's, and get their own tests there.
    private void registerFakeLogin() {
        server.register(PacketType.LOGIN_REQ, AuthLevel.ANONYMOUS, (session, envelope) -> {
            LoginRequest request = envelope.as(LoginRequest.class);
            User user = new User(request.username(), request.username(), "a@b.c", Gender.MALE,
                    request.passwordHash(), 0, "answer");
            server.attachAuthenticated(session, user);
            session.reply(envelope, new LoginResponse(true, "Welcome back!", null, "tok"));
        });
    }

    private TestClient connect() throws IOException {
        TestClient client = new TestClient(server.port());
        clients.add(client);
        return client;
    }

    // Presence is updated by the server's own threads, so a count is not guaranteed the instant the
    // client-side call returns. Polling for the expected value is what keeps this from being a sleep
    // that is either flaky or slow.
    private boolean awaitOnlineCount(int expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + REPLY_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (server.onlineCount() == expected) {
                return true;
            }
            Thread.sleep(20);
        }
        return false;
    }

}
