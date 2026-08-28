package server.match;

import models.game.Faction;
import net.dto.CardOffer;
import net.dto.EntityKind;
import net.dto.EntityState;
import net.dto.MatchEndReason;
import net.packets.AckResponse;
import net.packets.ChallengeAnswer;
import net.packets.ChallengeInvite;
import net.packets.ChallengeRequest;
import net.packets.CommandRejected;
import net.packets.GameCommand;
import net.packets.LoginRequest;
import net.packets.LoginResponse;
import net.packets.MatchOver;
import net.packets.MatchSnapshot;
import net.packets.MatchStart;
import net.packets.RegisterRequest;
import net.packets.RegisterResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import server.GameServer;
import server.TestClient;
import server.auth.AccountService;
import server.view.RelayRenderers;
import utils.Constants;
import utils.gameinitializers.GameInitializer;
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

// A real match, over real sockets: two clients, one authoritative board, and the rules that keep the
// two players out of each other's half.
//
// Everything here is checked against what a CLIENT can see. That is deliberate -- reaching into the
// runner's GameSession would confirm the model works, which VersusIZombieModeTest already does; what
// is untested until now is whether any of it survives the trip through a socket. A snapshot that is
// built correctly and serialised wrongly looks identical from inside the server.
class MatchRunnerTest {

    private static final String PASSWORD = "Str0ng!pass";

    // A three-second match. Long enough for thirty ticks of real play, short enough that "the clock
    // ran out" is a test that finishes rather than a test that is skipped -- and comfortably inside
    // TestClient's five-second push timeout.
    private static final int SHORT_MATCH_TICKS = 3 * Constants.TICKS_PER_SECOND;

    @BeforeAll
    static void loadGameData() {
        // Without this the registries are empty, every factory answers null, and a board that is
        // supposed to have five sun makers on it is simply blank -- which would let every assertion
        // below pass by describing nothing.
        new GameInitializer().loadAllData();
    }

    @TempDir
    Path directory;

    private AccountBackend previousBackend;
    private GameServer server;
    private MatchService matchService;
    private final List<TestClient> clients = new ArrayList<>();
    private final java.util.Map<TestClient, String> names = new java.util.HashMap<>();

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
        matchService.setMatchDurationTicks(SHORT_MATCH_TICKS);
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

    // ---- what a client is told before the first frame --------------------------------------------

    @Test
    @DisplayName("MatchStart carries both rosters, the red line and both banks")
    void matchStartDescribesTheWholeBoard() throws Exception {
        TestClient zombiePlayer = signedIn("Amir");
        TestClient plantPlayer = signedIn("Parsa");
        MatchStart[] starts = startMatch(zombiePlayer, plantPlayer);

        for (MatchStart start : starts) {
            assertFalse(start.zombieRoster().isEmpty(), "the zombie player needs something to buy");
            assertFalse(start.plantSeedBank().isEmpty(), "the plant player needs a seed bank");
            assertTrue(start.redLineColumn() > 0);
            assertEquals(Constants.BOARD_ROWS, start.rows());
            assertEquals(Constants.BOARD_COLS, start.cols());
            assertEquals(SHORT_MATCH_TICKS, start.matchDurationTicks());
            assertTrue(start.startingSunPlants() > 0);
            assertTrue(start.startingSunZombies() > 0);
        }
        // Both clients are told the same board -- and OPPOSITE sides of it.
        assertEquals(Faction.ZOMBIES, starts[0].yourFaction());
        assertEquals(Faction.PLANTS, starts[1].yourFaction());
        assertEquals("Parsa", starts[0].opponentUsername());
        assertEquals("Amir", starts[1].opponentUsername());
        assertEquals(starts[0].matchId(), starts[1].matchId());
    }

    @Test
    @DisplayName("every card carries the price the server will actually charge")
    void cardsArePriced() throws Exception {
        MatchStart start = startMatch(signedIn("Amir"), signedIn("Parsa"))[0];
        for (CardOffer offer : start.zombieRoster()) {
            assertTrue(offer.cost() > 0, offer.type() + " is free, which cannot be right");
        }
        for (CardOffer offer : start.plantSeedBank()) {
            // Priced from PlantRegistry rather than from a table in the server. A cost written down
            // twice is a cost that eventually disagrees with itself -- and the client would draw one
            // number while the server charged another.
            assertTrue(offer.cost() > 0, offer.type() + " is free, which cannot be right");
        }
    }

    // ---- the snapshot stream ---------------------------------------------------------------------

    @Test
    @DisplayName("the board arrives as snapshots and the clock runs down")
    void snapshotsStream() throws Exception {
        TestClient zombiePlayer = signedIn("Amir");
        TestClient plantPlayer = signedIn("Parsa");
        startMatch(zombiePlayer, plantPlayer);

        MatchSnapshot first = zombiePlayer.awaitPush(MatchSnapshot.class);
        MatchSnapshot later = awaitTickAfter(zombiePlayer, first.tick());

        assertTrue(later.tick() > first.tick(), "the simulation has to actually advance");
        assertTrue(later.ticksRemaining() < first.ticksRemaining(), "the clock has to run down");
        assertEquals(Constants.BOARD_ROWS, first.brainEaten().length);
        for (boolean eaten : first.brainEaten()) {
            assertFalse(eaten, "nothing has been eaten on the opening frame");
        }
        // Both players read the same board.
        assertNotNull(plantPlayer.awaitPush(MatchSnapshot.class));
    }

    @Test
    @DisplayName("the sun makers are on the opening board, flagged as what they are")
    void sunMakersAreInTheFirstSnapshot() throws Exception {
        TestClient zombiePlayer = signedIn("Amir");
        startMatch(zombiePlayer, signedIn("Parsa"));

        MatchSnapshot snapshot = zombiePlayer.awaitPush(MatchSnapshot.class);
        long makers = snapshot.entities().stream()
                .filter(entity -> entity.kind() == EntityKind.ZOMBIE)
                .filter(entity -> entity.is(net.dto.EntityFlags.SUN_PRODUCER))
                .count();
        // One per lane. The flag matters as much as the count: they are ordinary bucketheads in the
        // model, and only the mode knows which ones the view should draw as the disco mech.
        assertEquals(Constants.BOARD_ROWS, makers);
    }

    // ---- playing ---------------------------------------------------------------------------------

    @Test
    @DisplayName("a summon reaches the board and is paid for out of the zombie bank")
    void summoningWorks() throws Exception {
        TestClient zombiePlayer = signedIn("Amir");
        TestClient plantPlayer = signedIn("Parsa");
        MatchStart start = startMatch(zombiePlayer, plantPlayer)[0];

        MatchSnapshot before = zombiePlayer.awaitPush(MatchSnapshot.class);
        int lane = 3;
        zombiePlayer.send(new GameCommand("summon -t ZombieImp -l (" + (Constants.BOARD_COLS - 1)
                + ", " + lane + ")", before.tick()));

        MatchSnapshot after = awaitSnapshotWhere(zombiePlayer,
                snapshot -> snapshot.sunZombies() < start.startingSunZombies());
        assertTrue(countOf(after, EntityKind.ZOMBIE) > countOf(before, EntityKind.ZOMBIE),
                "the summoned zombie has to appear on the shared board");
        assertEquals(start.startingSunPlants(), after.sunPlants(),
                "and it must not be paid for out of the plant player's bank");
    }

    @Test
    @DisplayName("a plant reaches the board and is paid for out of the plant bank")
    void plantingWorks() throws Exception {
        TestClient zombiePlayer = signedIn("Amir");
        TestClient plantPlayer = signedIn("Parsa");
        MatchStart start = startMatch(zombiePlayer, plantPlayer)[1];

        MatchSnapshot before = plantPlayer.awaitPush(MatchSnapshot.class);
        plantPlayer.send(new GameCommand("plant plant -t Sunflower -l (0, 2)", before.tick()));

        MatchSnapshot after = awaitSnapshotWhere(plantPlayer,
                snapshot -> countOf(snapshot, EntityKind.PLANT) > 0);
        assertTrue(after.sunPlants() < start.startingSunPlants(), "a plant costs plant sun");
        assertEquals(start.startingSunZombies(), after.sunZombies(),
                "and never the zombie player's");
    }

    // ---- the half of the lawn that is not yours ---------------------------------------------------

    @Test
    @DisplayName("the plant player cannot summon and the zombie player cannot plant")
    void neitherPlayerCanUseTheOthersCommands() throws Exception {
        TestClient zombiePlayer = signedIn("Amir");
        TestClient plantPlayer = signedIn("Parsa");
        startMatch(zombiePlayer, plantPlayer);

        plantPlayer.send(new GameCommand("summon -t ZombieImp -l (8, 0)", 0));
        CommandRejected toPlantPlayer = plantPlayer.awaitPush(CommandRejected.class);
        assertNotNull(toPlantPlayer.reason());

        zombiePlayer.send(new GameCommand("plant plant -t Sunflower -l (0, 0)", 0));
        CommandRejected toZombiePlayer = zombiePlayer.awaitPush(CommandRejected.class);
        assertNotNull(toZombiePlayer.reason());

        // The refusal is the whole point, but so is the board: neither command may have half-landed.
        MatchSnapshot snapshot = awaitTickAfter(zombiePlayer, 4);
        assertEquals(0, countOf(snapshot, EntityKind.PLANT));
        assertEquals(Constants.BOARD_ROWS, countOf(snapshot, EntityKind.ZOMBIE),
                "only the five sun makers should be standing");
    }

    @Test
    @DisplayName("cheats and the clock are refused to both players")
    void cheatsAreRefused() throws Exception {
        TestClient zombiePlayer = signedIn("Amir");
        TestClient plantPlayer = signedIn("Parsa");
        startMatch(zombiePlayer, plantPlayer);

        // A whitelist, not a blacklist: none of these is named anywhere in FactionCommands, and that
        // is exactly why they are refused. "release the nuke" wins a match outright.
        for (String cheat : List.of("release the nuke", "cheat add -n 500 suns",
                "cheat remove-cooldown", "advance time -t 100 ticks", "exit game")) {
            zombiePlayer.send(new GameCommand(cheat, 0));
            assertNotNull(zombiePlayer.awaitPush(CommandRejected.class).reason(),
                    cheat + " must be refused");
        }
    }

    @Test
    @DisplayName("a refusal goes only to the player who earned it")
    void refusalsArePrivate() throws Exception {
        TestClient zombiePlayer = signedIn("Amir");
        TestClient plantPlayer = signedIn("Parsa");
        startMatch(zombiePlayer, plantPlayer);

        // A Gargantuar costs more than the opening bank holds, so this is the MODEL's refusal -- the
        // sentence a single-player game would put in a toast -- not the faction whitelist's.
        zombiePlayer.send(new GameCommand("summon -t ZombieGargantuar -l (8, 0)", 0));
        assertNotNull(zombiePlayer.awaitPush(CommandRejected.class).reason());

        // The plant player, who did nothing, must not hear about it. Every push on their socket is
        // looked at rather than waiting for a rejection that should never come -- the stream is ten
        // snapshots a second, so "nothing arrived" is not something a single poll can establish.
        assertFalse(sawRejection(plantPlayer, 1_200),
                "one player's refusals must not be broadcast to the other -- it tells them exactly "
                        + "what their opponent tried and could not afford");
    }

    // ---- reactions -------------------------------------------------------------------------------

    @Test
    @DisplayName("a reaction reaches the opponent, named, and does not echo back to the sender")
    void reactionsReachTheOpponent() throws Exception {
        TestClient zombiePlayer = signedIn("Amir");
        TestClient plantPlayer = signedIn("Parsa");
        startMatch(zombiePlayer, plantPlayer);

        zombiePlayer.send(new net.packets.ReactionSend(models.social.ReactionKind.TEXT, 0));

        net.packets.ReactionRelay relay = plantPlayer.awaitPush(net.packets.ReactionRelay.class);
        assertEquals("Amir", relay.fromUsername(), "the popup has to be able to say who");
        assertEquals(models.social.Reaction.GOOD_GAME,
                models.social.Reaction.of(relay.kind(), relay.index()));
        // Nothing comes back to the sender. Their own reaction is not news to them, and a client that
        // showed it would put its own taunt in the "your opponent says" corner.
        assertFalse(sawRelay(zombiePlayer, 800));
    }

    @Test
    @DisplayName("an index nobody has a reaction for is dropped rather than relayed")
    void nonsenseReactionsAreDropped() throws Exception {
        TestClient zombiePlayer = signedIn("Amir");
        TestClient plantPlayer = signedIn("Parsa");
        startMatch(zombiePlayer, plantPlayer);

        // The pair arrives off a socket. An index the catalogue does not cover would reach the other
        // client as a reaction it has no art and no text for -- an empty popup with a name on it.
        zombiePlayer.send(new net.packets.ReactionSend(models.social.ReactionKind.EMOJI, 99));
        assertFalse(sawRelay(plantPlayer, 800));
    }

    @Test
    @DisplayName("a spammed reaction bar cannot wallpaper the other player's screen")
    void reactionsAreRateLimited() throws Exception {
        TestClient zombiePlayer = signedIn("Amir");
        TestClient plantPlayer = signedIn("Parsa");
        startMatch(zombiePlayer, plantPlayer);

        for (int i = 0; i < 12; i++) {
            zombiePlayer.send(new net.packets.ReactionSend(models.social.ReactionKind.EMOJI, 0));
        }
        assertNotNull(plantPlayer.awaitPush(net.packets.ReactionRelay.class), "the first gets through");
        // And the rest do not. Enforced on the SERVER, which is the only place it holds: a client that
        // has been modified is not going to rate-limit itself.
        assertFalse(sawRelay(plantPlayer, 500), "a burst must arrive as one reaction, not twelve");
    }

    // ---- ending ----------------------------------------------------------------------------------

    @Test
    @DisplayName("the clock running out is a plant win, and both players are told the same thing")
    void theClockDecidesTheMatch() throws Exception {
        TestClient zombiePlayer = signedIn("Amir");
        TestClient plantPlayer = signedIn("Parsa");
        startMatch(zombiePlayer, plantPlayer);

        MatchOver forZombies = zombiePlayer.awaitPush(MatchOver.class);
        MatchOver forPlants = plantPlayer.awaitPush(MatchOver.class);

        assertEquals(Faction.PLANTS, forZombies.winner());
        assertEquals(Faction.PLANTS, forPlants.winner());
        assertEquals(MatchEndReason.TIME_UP, forZombies.reason());
        assertEquals(Constants.BOARD_ROWS, forZombies.brainsTotal());
        assertEquals(0, forZombies.brainsEaten());
        assertEquals(0, matchService.liveMatches());
    }

    @Test
    @DisplayName("the result lands on both profiles")
    void versusRecordIsKept() throws Exception {
        TestClient zombiePlayer = signedIn("Amir");
        TestClient plantPlayer = signedIn("Parsa");
        startMatch(zombiePlayer, plantPlayer);
        plantPlayer.awaitPush(MatchOver.class);

        // The plants held the lawn, so Parsa took the win and Amir the loss. Read off the live roster,
        // which is the same object the leaderboard reads.
        assertEquals(1, DatabaseManager.getInstance().findUser("Parsa").getProfile().getVersusWins());
        assertEquals(1,
                DatabaseManager.getInstance().findUser("Amir").getProfile().getVersusLosses());
    }

    @Test
    @DisplayName("neither of the two spec-verbatim banners is relayed to either player")
    void theOutcomeBannersNeverGoOverTheWire() throws Exception {
        TestClient zombiePlayer = signedIn("Amir");
        TestClient plantPlayer = signedIn("Parsa");
        startMatch(zombiePlayer, plantPlayer);

        // GameEngine renders one of them at the end of every level. Exactly ONE of these two players
        // should ever see each, so the server sends neither and each client draws its own from
        // MatchOver.winner -- otherwise the plant player, who just WON, reads "The zombie ate your
        // brain; LOSER!!!" because the mode is written from the zombie player's seat.
        //
        // Watched all the way to MatchOver rather than checked afterwards: the banner is rendered on
        // the very tick the match ends, so anything that only starts looking once MatchOver is in hand
        // is looking after the one packet it is looking for could possibly have arrived.
        assertNotNull(watchForBannersUntilMatchOver(zombiePlayer));
        assertNotNull(watchForBannersUntilMatchOver(plantPlayer));
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private static int countOf(MatchSnapshot snapshot, EntityKind kind) {
        int n = 0;
        for (EntityState entity : snapshot.entities()) {
            if (entity.kind() == kind) {
                n++;
            }
        }
        return n;
    }

    private MatchSnapshot awaitTickAfter(TestClient client, long tick) throws Exception {
        return awaitSnapshotWhere(client, snapshot -> snapshot.tick() > tick);
    }

    // Snapshots arrive ten a second and a command takes at least a tick to show up in one, so every
    // assertion about the board has to wait for a frame that satisfies it rather than reading the next
    // one and hoping.
    private MatchSnapshot awaitSnapshotWhere(TestClient client,
                                             java.util.function.Predicate<MatchSnapshot> until)
            throws Exception {
        for (int i = 0; i < 60; i++) {
            MatchSnapshot snapshot = client.awaitPush(MatchSnapshot.class);
            if (until.test(snapshot)) {
                return snapshot;
            }
        }
        throw new AssertionError("no snapshot matched within 60 frames");
    }

    // Whether a CommandRejected turns up anywhere in this client's stream in the next `millis`.
    private boolean sawRejection(TestClient client, long millis) throws Exception {
        return sawPush(client, millis, CommandRejected.class);
    }

    private boolean sawRelay(TestClient client, long millis) throws Exception {
        return sawPush(client, millis, net.packets.ReactionRelay.class);
    }

    // Every push on this client's socket is looked at, rather than waiting for one of a type that
    // should never come: during a match the stream is ten snapshots a second, so "nothing arrived" is
    // not something a single poll can establish.
    private boolean sawPush(TestClient client, long millis, Class<? extends net.Packet> wanted)
            throws Exception {
        long deadline = System.currentTimeMillis() + millis;
        long remaining;
        while ((remaining = deadline - System.currentTimeMillis()) > 0) {
            net.Envelope push = client.pollPush(remaining);
            if (push == null) {
                return false;
            }
            if (wanted.isInstance(push.payload())) {
                return true;
            }
        }
        return false;
    }

    // Reads every push until the match ends, failing the moment one of the two spec-verbatim banners
    // comes through. Returns the MatchOver.
    private MatchOver watchForBannersUntilMatchOver(TestClient client) throws Exception {
        long deadline = System.currentTimeMillis() + TestClient.REPLY_TIMEOUT_MS;
        long remaining;
        while ((remaining = deadline - System.currentTimeMillis()) > 0) {
            net.Envelope push = client.pollPush(remaining);
            if (push == null) {
                break;
            }
            if (push.payload() instanceof net.packets.MatchEvent event) {
                assertFalse(RelayRenderers.isOutcomeBanner(event.text()),
                        "the server relayed an outcome banner: " + event.text());
            }
            if (push.payload() instanceof MatchOver over) {
                return over;
            }
        }
        throw new AssertionError("the match never ended");
    }

    // Returns {the challenger's MatchStart, the target's} -- challenger takes zombies.
    private MatchStart[] startMatch(TestClient challenger, TestClient target) throws Exception {
        challenger.request(new ChallengeRequest(names.get(target), Faction.ZOMBIES),
                AckResponse.class);
        ChallengeInvite invite = target.awaitPush(ChallengeInvite.class);
        target.request(new ChallengeAnswer(invite.challengeId(), true), AckResponse.class);
        return new MatchStart[] {
                challenger.awaitPush(MatchStart.class),
                target.awaitPush(MatchStart.class)
        };
    }

    private TestClient signedIn(String username) throws Exception {
        TestClient client = TestClient.connected(server.port());
        clients.add(client);
        names.put(client, username);
        assertTrue(client.request(new RegisterRequest(username, PasswordHasher.hash(PASSWORD),
                username, username.toLowerCase() + "@example.com", "MALE", 0,
                SecurityAnswer.hash("blue")), RegisterResponse.class).ok());
        assertTrue(client.request(new LoginRequest(username, PasswordHasher.hash(PASSWORD), false),
                LoginResponse.class).ok());
        return client;
    }
}
