package views.net;

import models.game.Faction;
import net.Envelope;
import net.PacketType;
import net.Protocol;
import net.packets.AckResponse;
import net.packets.ChallengeRejected;
import net.packets.ChallengeRequest;
import net.packets.LoginRequest;
import net.packets.LoginResponse;
import net.packets.RegisterRequest;
import net.packets.RegisterResponse;
import net.dto.ChallengeRejectReason;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import server.GameServer;
import server.TestClient;
import server.auth.AccountService;
import server.match.MatchService;
import utils.storage.AccountBackend;
import utils.storage.DatabaseManager;
import utils.storage.LocalFileBackend;
import utils.storage.PasswordHasher;
import utils.storage.SecurityAnswer;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The SHIPPED client against a real server.
//
// Every other socket test in this project drives TestClient, a test double that asks for the type it
// already knows is coming. That proved the server sends the right thing and could never prove the
// client can read it -- and the gap was real: OnlineScreen asked for AckResponse alone, so a challenge
// to an offline player came back as a ChallengeRejected, failed the cast, returned null, and was
// reported to the player as "the server isn't answering". The one thing that had not happened.
//
// So this exercises NetClient itself. It has no LibGDX imports, which is what makes that possible.
class NetClientTest {

    private static final String PASSWORD = "Str0ng!pass";

    @TempDir
    Path directory;

    private AccountBackend previousBackend;
    private String previousServerProperty;
    private GameServer server;
    private MatchService matchService;
    private NetClient client;

    @BeforeEach
    void startServer() throws Exception {
        previousBackend = DatabaseManager.getInstance().backend();
        DatabaseManager.setBackend(new LocalFileBackend(directory.resolve("server.json").toString()));

        server = new GameServer(0);
        server.setLog(message -> { });
        server.setErrorLog(message -> { });
        server.start();
        new AccountService(server).registerHandlers();
        matchService = new MatchService(server);
        matchService.registerHandlers();

        // Registered and then disconnected: on the roster, not online. Seeded through TestClient
        // because seeding is not what is under test here.
        try (TestClient seed = TestClient.connected(server.port())) {
            seed.request(registration("Sleeper"), RegisterResponse.class);
        }

        previousServerProperty = System.getProperty(Protocol.SERVER_PROPERTY);
        System.setProperty(Protocol.SERVER_PROPERTY, "127.0.0.1:" + server.port());
        client = new NetClient();
        client.configureFromSystemProperties();
        assertTrue(client.connect(), client.lastError());
        assertTrue(client.request(registration("Amir"), RegisterResponse.class).ok());
        assertTrue(client.request(new LoginRequest("Amir", PasswordHasher.hash(PASSWORD), false),
                LoginResponse.class).ok());
    }

    @AfterEach
    void stopServer() {
        if (client != null) {
            client.close();
        }
        if (matchService != null) {
            matchService.shutdown();
        }
        if (server != null) {
            server.close();
        }
        if (previousServerProperty == null) {
            System.clearProperty(Protocol.SERVER_PROPERTY);
        } else {
            System.setProperty(Protocol.SERVER_PROPERTY, previousServerProperty);
        }
        if (previousBackend != null) {
            DatabaseManager.setBackend(previousBackend);
        }
    }

    @Test
    @DisplayName("a refusal is readable through the shipped client, on the reply channel")
    void offlineRefusalIsReadable() {
        Envelope reply = client.requestRaw(new ChallengeRequest("Sleeper", Faction.PLANTS));

        assertNotNull(reply, "the server answered; the client has to be able to see it");
        assertEquals(PacketType.CHALLENGE_REJECTED, reply.type());
        assertEquals(ChallengeRejectReason.OFFLINE, reply.as(ChallengeRejected.class).reason(),
                "and it must be able to tell an offline friend from a misspelled name");
    }

    // The regression itself, pinned.
    //
    // Naming ONE expected type is not merely less convenient here -- it is wrong, and it fails by
    // returning null, which is indistinguishable from the server being dead. This asserts the trap is
    // still there so nobody quietly reintroduces the old call and wonders why the message came back.
    @Test
    @DisplayName("asking for a single type turns a refusal into silence -- which is why requestRaw exists")
    void namingOneTypeDiscardsTheRefusal() {
        assertNull(client.request(new ChallengeRequest("Sleeper", Faction.PLANTS), AckResponse.class));
        assertTrue(client.lastError().contains("CHALLENGE_REJECTED"),
                "and the reason is recoverable from lastError(): " + client.lastError());
    }

    // The reply slot has to BUFFER, and this is the only cheap way to prove it does.
    //
    // A caller cannot already be waiting when it sends -- it registers a slot, writes to the socket,
    // and only then parks -- so a reply that arrives inside that window has to be held for it. With a
    // SynchronousQueue, which is what this was, offer() needs a consumer parked at that instant and
    // otherwise discards the reply; the caller then waits out the whole five-second timeout and reports
    // a server that answered immediately as one that never answered. On loopback that window is wide
    // enough to lose a handshake, and the full suite lost one about a third of the time.
    //
    // Many small round trips rather than one, because the race is timing and a single trip is a single
    // roll of the dice. Every one of them has to come back, and quickly.
    @Test
    @DisplayName("a reply that beats the caller to the wait is held for it, not dropped")
    void repliesThatArriveEarlyAreNotLost() {
        long started = System.nanoTime();
        for (int attempt = 0; attempt < 40; attempt++) {
            assertNotNull(client.requestRaw(new ChallengeRequest("Sleeper", Faction.PLANTS)),
                    "round trip " + attempt + " was dropped: " + client.lastError());
        }
        // A dropped reply does not fail the assertion above on its own -- it costs five seconds first.
        // Forty answered round trips over a loopback socket are milliseconds; this fails long before
        // the assertion would if even one of them had to time out.
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;
        assertTrue(elapsedMs < 5_000, "forty local round trips took " + elapsedMs
                + "ms, which means at least one reply was thrown away and waited out its timeout");
    }

    @Test
    @DisplayName("a name nobody has registered is refused differently from one who is merely away")
    void unknownNameIsItsOwnRefusal() {
        Envelope reply = client.requestRaw(new ChallengeRequest("Nobody", Faction.PLANTS));
        assertEquals(ChallengeRejectReason.NO_SUCH_USER, reply.as(ChallengeRejected.class).reason());
    }

    @Test
    @DisplayName("a challenge that IS delivered acknowledges on the same channel")
    void deliveredChallengeAcknowledges() throws Exception {
        try (TestClient parsa = TestClient.connected(server.port())) {
            parsa.request(registration("Parsa"), RegisterResponse.class);
            parsa.request(new LoginRequest("Parsa", PasswordHasher.hash(PASSWORD), false),
                    LoginResponse.class);

            Envelope reply = client.requestRaw(new ChallengeRequest("Parsa", Faction.ZOMBIES));
            assertEquals(PacketType.ACK, reply.type());
            assertTrue(reply.as(AckResponse.class).ok());
        }
    }

    private static RegisterRequest registration(String username) {
        return new RegisterRequest(username, PasswordHasher.hash(PASSWORD), "Gardener",
                "gardener@example.com", "MALE", 0, SecurityAnswer.hash("fluffy"));
    }
}
