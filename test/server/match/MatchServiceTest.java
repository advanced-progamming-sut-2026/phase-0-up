package server.match;

import net.PacketType;
import net.dto.ChallengeRejectReason;
import net.dto.Faction;
import net.dto.MatchEndReason;
import net.packets.AckResponse;
import net.packets.ChallengeAnswer;
import net.packets.ChallengeDeclined;
import net.packets.ChallengeInvite;
import net.packets.ChallengeRejected;
import net.packets.ChallengeRequest;
import net.packets.LoginRequest;
import net.packets.LoginResponse;
import net.packets.MatchLeaveRequest;
import net.packets.MatchOver;
import net.packets.MatchStart;
import net.packets.OnlineUsersRequest;
import net.packets.OnlineUsersResponse;
import net.packets.OpponentDisconnected;
import net.packets.QueueJoinRequest;
import net.packets.QueueLeaveRequest;
import net.packets.QueueStatus;
import net.packets.RegisterRequest;
import net.packets.RegisterResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import server.GameServer;
import server.TestClient;
import server.auth.AccountService;
import utils.storage.AccountBackend;
import utils.storage.DatabaseManager;
import utils.storage.LocalFileBackend;
import utils.storage.PasswordHasher;
import utils.storage.SecurityAnswer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Finding an opponent: both routes the spec asks for, over real sockets.
//
// Every test signs players in for real rather than faking presence, because the whole point of the
// lobby is that it works off the SAME map that answers "is this person online" -- and a test that
// stubbed that would be testing a different program.
class MatchServiceTest {

    private static final String PASSWORD = "Str0ng!pass";

    @TempDir
    Path directory;

    private AccountBackend previousBackend;
    private GameServer server;
    private MatchService matchService;
    private final List<TestClient> clients = new ArrayList<>();

    @BeforeEach
    void startServer() throws IOException {
        previousBackend = DatabaseManager.getInstance().backend();
        DatabaseManager.setBackend(new LocalFileBackend(directory.resolve("server.json").toString()));

        server = new GameServer(0);
        server.setLog(message -> { });
        server.setErrorLog(message -> { });
        server.start();
        new AccountService(server).registerHandlers();
        matchService = new MatchService(server);
        matchService.registerHandlers();
    }

    @AfterEach
    void stopServer() {
        for (TestClient client : clients) {
            client.close();
        }
        clients.clear();
        if (matchService != null) {
            matchService.shutdown();
        }
        if (server != null) {
            server.close();
        }
        if (previousBackend != null) {
            DatabaseManager.setBackend(previousBackend);
        }
    }

    // ---- the lobby list -------------------------------------------------------------------------

    @Test
    @DisplayName("the lobby lists everybody available except you")
    void lobbyListsOthers() throws Exception {
        TestClient amir = signedIn("Amir");
        TestClient parsa = signedIn("Parsa");

        assertEquals(List.of("Parsa"),
                amir.request(new OnlineUsersRequest(), OnlineUsersResponse.class).usernames());
        assertEquals(List.of("Amir"),
                parsa.request(new OnlineUsersRequest(), OnlineUsersResponse.class).usernames());
    }

    @Test
    @DisplayName("a player already in a match is not offered as an opponent")
    void playersInMatchesAreNotListed() throws Exception {
        TestClient amir = signedIn("Amir");
        TestClient parsa = signedIn("Parsa");
        TestClient bea = signedIn("Bea");

        startMatch(amir, parsa);

        // "Available" is a fact only the server holds -- a client cannot know somebody started a match
        // a moment ago, which is why the filtering is not left to the lobby screen.
        assertEquals(List.of(),
                bea.request(new OnlineUsersRequest(), OnlineUsersResponse.class).usernames());
    }

    // ---- direct challenge -----------------------------------------------------------------------

    @Test
    @DisplayName("challenging a real, online player puts a pop-up on their screen")
    void challengeReachesTheTarget() throws Exception {
        TestClient amir = signedIn("Amir");
        TestClient parsa = signedIn("Parsa");

        AckResponse sent = amir.request(new ChallengeRequest("Parsa", Faction.ZOMBIES),
                AckResponse.class);
        assertTrue(sent.ok(), sent.message());

        ChallengeInvite invite = parsa.awaitPush(ChallengeInvite.class);
        assertEquals("Amir", invite.fromUsername());
        assertEquals(Faction.ZOMBIES, invite.theirFaction());
        assertNotNull(invite.challengeId());
        assertTrue(invite.expiresInSeconds() > 0, "the pop-up must be able to count itself down");
    }

    @Test
    @DisplayName("a misspelled name and an offline friend are told apart")
    void invalidAndOfflineAreDistinct() throws Exception {
        TestClient amir = signedIn("Amir");
        // Registered but not connected -- the "offline" case.
        register(amir, "Sleeper");

        // The spec asks for "an appropriate error" here. One message for both would leave the player
        // unable to tell a typo from a friend who is asleep.
        assertEquals(ChallengeRejectReason.NO_SUCH_USER,
                amir.request(new ChallengeRequest("Nobody", Faction.PLANTS),
                        ChallengeRejected.class).reason());
        assertEquals(ChallengeRejectReason.OFFLINE,
                amir.request(new ChallengeRequest("Sleeper", Faction.PLANTS),
                        ChallengeRejected.class).reason());
    }

    @Test
    @DisplayName("you cannot challenge yourself")
    void selfChallengeIsRefused() throws Exception {
        TestClient amir = signedIn("Amir");
        assertEquals(ChallengeRejectReason.SELF,
                amir.request(new ChallengeRequest("amir", Faction.PLANTS),
                        ChallengeRejected.class).reason());
    }

    @Test
    @DisplayName("a player already in a match cannot be challenged")
    void busyPlayerIsRefused() throws Exception {
        TestClient amir = signedIn("Amir");
        TestClient parsa = signedIn("Parsa");
        TestClient bea = signedIn("Bea");
        startMatch(amir, parsa);

        assertEquals(ChallengeRejectReason.IN_MATCH,
                bea.request(new ChallengeRequest("Amir", Faction.PLANTS),
                        ChallengeRejected.class).reason());
    }

    @Test
    @DisplayName("accepting starts the match, and each player is told their own side")
    void acceptingStartsTheMatch() throws Exception {
        TestClient amir = signedIn("Amir");
        TestClient parsa = signedIn("Parsa");

        amir.request(new ChallengeRequest("Parsa", Faction.ZOMBIES), AckResponse.class);
        ChallengeInvite invite = parsa.awaitPush(ChallengeInvite.class);
        parsa.request(new ChallengeAnswer(invite.challengeId(), true), AckResponse.class);

        MatchStart forAmir = amir.awaitPush(MatchStart.class);
        MatchStart forParsa = parsa.awaitPush(MatchStart.class);

        // The challenger asked for zombies and gets them; the accepter takes the other side. Somebody
        // has to choose, and the person who did the inviting is the reasonable one.
        assertEquals(Faction.ZOMBIES, forAmir.yourFaction());
        assertEquals(Faction.PLANTS, forParsa.yourFaction());
        assertEquals("Parsa", forAmir.opponentUsername());
        assertEquals("Amir", forParsa.opponentUsername());
        // Same match, different packets -- which is exactly why MatchStart is not broadcast.
        assertEquals(forAmir.matchId(), forParsa.matchId());
        assertEquals(1, matchService.liveMatches());
    }

    @Test
    @DisplayName("declining tells the challenger, and starts nothing")
    void decliningIsReported() throws Exception {
        TestClient amir = signedIn("Amir");
        TestClient parsa = signedIn("Parsa");

        amir.request(new ChallengeRequest("Parsa", Faction.PLANTS), AckResponse.class);
        ChallengeInvite invite = parsa.awaitPush(ChallengeInvite.class);
        parsa.request(new ChallengeAnswer(invite.challengeId(), false), AckResponse.class);

        ChallengeDeclined declined = amir.awaitPush(ChallengeDeclined.class);
        assertEquals("Parsa", declined.byUsername());
        assertFalse(declined.timedOut(), "a person said no; that is not a timeout");
        assertEquals(0, matchService.liveMatches());
    }

    @Test
    @DisplayName("answering a challenge twice is refused rather than starting two matches")
    void staleChallengeAnswerIsRefused() throws Exception {
        TestClient amir = signedIn("Amir");
        TestClient parsa = signedIn("Parsa");

        amir.request(new ChallengeRequest("Parsa", Faction.PLANTS), AckResponse.class);
        ChallengeInvite invite = parsa.awaitPush(ChallengeInvite.class);
        assertTrue(parsa.request(new ChallengeAnswer(invite.challengeId(), true),
                AckResponse.class).ok());

        // A double-click on the pop-up, or a click a moment after it closed itself.
        assertFalse(parsa.request(new ChallengeAnswer(invite.challengeId(), true),
                AckResponse.class).ok());
        assertEquals(1, matchService.liveMatches());
    }

    @Test
    @DisplayName("a second challenge replaces the first, so nobody can paper the lobby")
    void onlyOneOutgoingChallengeAtATime() throws Exception {
        TestClient amir = signedIn("Amir");
        TestClient parsa = signedIn("Parsa");
        TestClient bea = signedIn("Bea");

        amir.request(new ChallengeRequest("Parsa", Faction.PLANTS), AckResponse.class);
        parsa.awaitPush(ChallengeInvite.class);
        amir.request(new ChallengeRequest("Bea", Faction.PLANTS), AckResponse.class);
        bea.awaitPush(ChallengeInvite.class);

        // Otherwise a player could invite everybody and then accept several at once, ending up in
        // more than one match.
        assertEquals(1, matchService.pendingChallenges());
    }

    // ---- random queue ---------------------------------------------------------------------------

    @Test
    @DisplayName("the first player waits; the second is paired with them at once")
    void queuePairsTwoPlayers() throws Exception {
        TestClient amir = signedIn("Amir");
        TestClient parsa = signedIn("Parsa");

        QueueStatus waiting = amir.request(new QueueJoinRequest(Faction.PLANTS), QueueStatus.class);
        assertTrue(waiting.waiting());
        assertEquals(1, matchService.queueSize());

        parsa.send(new QueueJoinRequest(Faction.PLANTS));

        MatchStart forAmir = amir.awaitPush(MatchStart.class);
        MatchStart forParsa = parsa.awaitPush(MatchStart.class);

        // Both asked for plants. Whoever was WAITING gets their preference -- the only tie-break here
        // that is not arbitrary is rewarding the wait.
        assertEquals(Faction.PLANTS, forAmir.yourFaction());
        assertEquals(Faction.ZOMBIES, forParsa.yourFaction());
        assertEquals(0, matchService.queueSize(), "both players leave the queue on pairing");
        assertEquals(1, matchService.liveMatches());
    }

    @Test
    @DisplayName("leaving the queue means the next arrival waits instead of pairing")
    void leavingTheQueueWorks() throws Exception {
        TestClient amir = signedIn("Amir");
        TestClient parsa = signedIn("Parsa");

        amir.request(new QueueJoinRequest(Faction.PLANTS), QueueStatus.class);
        assertFalse(amir.request(new QueueLeaveRequest(), QueueStatus.class).waiting());
        assertEquals(0, matchService.queueSize());

        assertTrue(parsa.request(new QueueJoinRequest(Faction.PLANTS), QueueStatus.class).waiting());
        assertEquals(0, matchService.liveMatches());
    }

    @Test
    @DisplayName("a queued player who disconnects does not block the queue forever")
    void staleQueueEntryIsDiscarded() throws Exception {
        TestClient ghost = signedIn("Ghost");
        ghost.request(new QueueJoinRequest(Faction.PLANTS), QueueStatus.class);
        ghost.close();

        TestClient amir = signedIn("Amir");
        TestClient parsa = signedIn("Parsa");

        // Without discarding the dead entry, it would sit at the head of the queue and pair with
        // nobody -- and every later player would wait behind a player who is not there.
        amir.request(new QueueJoinRequest(Faction.PLANTS), QueueStatus.class);
        parsa.send(new QueueJoinRequest(Faction.ZOMBIES));

        assertNotNull(amir.awaitPush(MatchStart.class));
        assertNotNull(parsa.awaitPush(MatchStart.class));
    }

    @Test
    @DisplayName("joining the queue twice does not pair a player with themselves")
    void doubleQueueJoinIsSafe() throws Exception {
        TestClient amir = signedIn("Amir");
        amir.request(new QueueJoinRequest(Faction.PLANTS), QueueStatus.class);
        amir.request(new QueueJoinRequest(Faction.PLANTS), QueueStatus.class);

        assertEquals(1, matchService.queueSize(), "one entry, not two");
        assertEquals(0, matchService.liveMatches(), "and certainly not a match against yourself");
    }

    // ---- leaving and dropping -------------------------------------------------------------------

    @Test
    @DisplayName("leaving a match is a forfeit, and both players hear the same result")
    void leavingForfeits() throws Exception {
        TestClient amir = signedIn("Amir");
        TestClient parsa = signedIn("Parsa");
        startMatch(amir, parsa);   // Amir zombies, Parsa plants

        amir.request(new MatchLeaveRequest(), AckResponse.class);

        MatchOver forAmir = amir.awaitPush(MatchOver.class);
        MatchOver forParsa = parsa.awaitPush(MatchOver.class);

        // The SAME packet naming the winning FACTION, not "you won" -- each client compares it against
        // the side it was given. From the server both statements are true at once.
        assertEquals(Faction.PLANTS, forAmir.winner());
        assertEquals(Faction.PLANTS, forParsa.winner());
        assertEquals(MatchEndReason.OPPONENT_LEFT, forAmir.reason());
        assertEquals(0, matchService.liveMatches());
    }

    @Test
    @DisplayName("a dropped connection forfeits the match and tells the player still there")
    void disconnectForfeits() throws Exception {
        TestClient amir = signedIn("Amir");
        TestClient parsa = signedIn("Parsa");
        startMatch(amir, parsa);   // Amir zombies, Parsa plants

        amir.close();

        OpponentDisconnected gone = parsa.awaitPush(OpponentDisconnected.class);
        assertEquals("Amir", gone.username());

        MatchOver over = parsa.awaitPush(MatchOver.class);
        assertEquals(Faction.PLANTS, over.winner(), "the player still connected wins");
        assertEquals(MatchEndReason.OPPONENT_LEFT, over.reason());
        assertEquals(0, matchService.liveMatches());
    }

    @Test
    @DisplayName("a disconnect withdraws the challenge that was waiting on that player")
    void disconnectWithdrawsChallenges() throws Exception {
        TestClient amir = signedIn("Amir");
        TestClient parsa = signedIn("Parsa");

        amir.request(new ChallengeRequest("Parsa", Faction.PLANTS), AckResponse.class);
        parsa.awaitPush(ChallengeInvite.class);
        parsa.close();

        // The pop-up is on a screen that no longer exists. Without this the challenger sits waiting
        // out the full timeout for an answer that can never come.
        ChallengeDeclined declined = amir.awaitPush(ChallengeDeclined.class);
        assertTrue(declined.timedOut());
        assertEquals(0, matchService.pendingChallenges());
    }

    @Test
    @DisplayName("matchmaking is not available before signing in")
    void matchmakingNeedsAuthentication() throws Exception {
        TestClient stranger = TestClient.connected(server.port());
        clients.add(stranger);

        var reply = stranger.requestRaw(new ChallengeRequest("Amir", Faction.PLANTS));
        assertEquals(PacketType.ACK, reply.type());
        assertFalse(reply.as(AckResponse.class).ok());
    }

    @Test
    @DisplayName("leaving a match you are not in is refused rather than crashing")
    void leavingNoMatchIsRefused() throws Exception {
        TestClient amir = signedIn("Amir");
        assertFalse(amir.request(new MatchLeaveRequest(), AckResponse.class).ok());
    }

    // ---- helpers --------------------------------------------------------------------------------

    // Puts two players into a match: `challenger` takes zombies, `target` takes plants.
    private void startMatch(TestClient challenger, TestClient target) throws Exception {
        String name = usernameOf(challenger);
        challenger.request(new ChallengeRequest(usernameOf(target), Faction.ZOMBIES),
                AckResponse.class);
        ChallengeInvite invite = target.awaitPush(ChallengeInvite.class);
        assertEquals(name, invite.fromUsername());
        target.request(new ChallengeAnswer(invite.challengeId(), true), AckResponse.class);
        challenger.awaitPush(MatchStart.class);
        target.awaitPush(MatchStart.class);
    }

    private final java.util.Map<TestClient, String> names = new java.util.HashMap<>();

    private String usernameOf(TestClient client) {
        return names.get(client);
    }

    private TestClient signedIn(String username) throws Exception {
        TestClient client = TestClient.connected(server.port());
        clients.add(client);
        names.put(client, username);
        register(client, username);
        assertTrue(client.request(new LoginRequest(username, PasswordHasher.hash(PASSWORD), false),
                LoginResponse.class).ok());
        return client;
    }

    private void register(TestClient client, String username) throws Exception {
        client.request(new RegisterRequest(username, PasswordHasher.hash(PASSWORD), "Gardener",
                "g@example.com", "MALE", 0, SecurityAnswer.hash("fluffy")), RegisterResponse.class);
    }

}
