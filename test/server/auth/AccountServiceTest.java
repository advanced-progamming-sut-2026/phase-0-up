package server.auth;

import models.user.Gender;
import models.user.User;
import net.PacketType;
import net.packets.AckResponse;
import net.packets.LoginRequest;
import net.packets.LoginResponse;
import net.packets.LeaderboardRequest;
import net.packets.LeaderboardResponse;
import net.packets.LogoutRequest;
import net.packets.PasswordChangeRequest;
import net.packets.ProfileSyncRequest;
import net.packets.ProfileSyncResponse;
import net.packets.RecoveryQuestionRequest;
import net.packets.RecoveryQuestionResponse;
import net.packets.RecoverySubmitRequest;
import net.packets.RegisterRequest;
import net.packets.RegisterResponse;
import net.packets.UsernameCheckRequest;
import net.packets.UsernameCheckResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import server.GameServer;
import server.TestClient;
import utils.storage.AccountBackend;
import utils.storage.DatabaseManager;
import utils.storage.LocalFileBackend;
import utils.storage.PasswordHasher;
import utils.storage.SecurityAnswer;
import utils.storage.records.UserRecord;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Accounts, over a real socket, against a real server.
//
// ## The backend is swapped, and put back
//
// DatabaseManager is a process-wide singleton, and these tests point it at a @TempDir so they never
// touch the project's users_database.json. That swap has to be UNDONE afterwards, or every test that
// runs later in the same JVM inherits a roster in a temp directory that no longer exists -- a failure
// that would appear in a completely unrelated file and look like anything but this one.
class AccountServiceTest {

    private static final String GOOD_PASSWORD = "Str0ng!pass";

    @TempDir
    Path directory;

    private AccountBackend previousBackend;
    private GameServer server;
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
        // Always, even if a test failed. See the note at the top of the class.
        if (previousBackend != null) {
            DatabaseManager.setBackend(previousBackend);
        }
    }

    // ---- registration ---------------------------------------------------------------------------

    @Test
    @DisplayName("a new player can register")
    void registerSucceeds() throws Exception {
        TestClient client = connect();
        RegisterResponse response = client.request(registration("Amir"), RegisterResponse.class);

        assertTrue(response.ok(), response.message());
        assertNotNull(response.user());
        assertEquals("Amir", response.user().getUsername());
        assertTrue(DatabaseManager.getInstance().usernameExists("Amir"));
    }

    @Test
    @DisplayName("registering does NOT sign you in -- that is still the login screen's job")
    void registerDoesNotAuthenticate() throws Exception {
        TestClient client = connect();
        client.request(registration("Amir"), RegisterResponse.class);

        // The terminal build has always dropped a new player on the login menu. Signing them in here
        // would make the two front ends disagree about what registering does.
        assertEquals(0, server.onlineCount());
    }

    @Test
    @DisplayName("usernames are globally unique, no matter the capitals")
    void duplicateUsernameIsRefused() throws Exception {
        TestClient first = connect();
        assertTrue(first.request(registration("Amir"), RegisterResponse.class).ok());

        // This is THE spec requirement for registration -- and it is global now precisely because the
        // roster is here rather than on each player's machine.
        TestClient second = connect();
        RegisterResponse response = second.request(registration("amir"), RegisterResponse.class);

        assertFalse(response.ok());
        assertNull(response.user());
        assertEquals(1, DatabaseManager.getInstance().getAllUsers().size());
    }

    @Test
    @DisplayName("a malformed username is refused, with a message that says which rule broke")
    void malformedUsernameIsRefused() throws Exception {
        TestClient client = connect();
        RegisterResponse response = client.request(registration("not a username!"),
                RegisterResponse.class);

        assertFalse(response.ok());
        // Reusing UsernameValidator rather than inventing a server-side rule is what keeps this
        // sentence identical to the one the terminal build produces.
        assertTrue(response.message().toLowerCase().contains("letters"), response.message());
    }

    @Test
    @DisplayName("a password that arrived unhashed is refused rather than stored")
    void unhashedPasswordIsRefused() throws Exception {
        // The server cannot grade password STRENGTH -- it never sees the plaintext, which is the whole
        // point. What it can and must refuse is a client that skipped hashing altogether, because that
        // would put a readable password in the roster file permanently.
        TestClient client = connect();
        RegisterRequest raw = new RegisterRequest("Amir", GOOD_PASSWORD, "Amir",
                "amir@example.com", "MALE", 0, SecurityAnswer.hash("fluffy"));

        RegisterResponse response = client.request(raw, RegisterResponse.class);
        assertFalse(response.ok());
        assertTrue(response.message().contains("hashed"), response.message());
        assertFalse(DatabaseManager.getInstance().usernameExists("Amir"));
    }

    @Test
    @DisplayName("a username can be checked before the form is submitted")
    void usernameCheckWorksBeforeSignIn() throws Exception {
        TestClient client = connect();
        assertFalse(client.request(new UsernameCheckRequest("Amir"), UsernameCheckResponse.class)
                .taken());

        client.request(registration("Amir"), RegisterResponse.class);

        assertTrue(client.request(new UsernameCheckRequest("aMiR"), UsernameCheckResponse.class)
                .taken());
    }

    // ---- signing in -----------------------------------------------------------------------------

    @Test
    @DisplayName("correct credentials sign you in and put you on the online list")
    void loginSucceeds() throws Exception {
        TestClient client = connect();
        client.request(registration("Amir"), RegisterResponse.class);

        LoginResponse response = client.request(login("Amir", GOOD_PASSWORD, false),
                LoginResponse.class);

        assertTrue(response.ok(), response.message());
        assertNotNull(response.user());
        assertNotNull(response.sessionToken());
        assertTrue(server.isOnline("Amir"));
    }

    @Test
    @DisplayName("a wrong password and an unknown user are both refused, and neither signs you in")
    void badCredentialsAreRefused() throws Exception {
        TestClient client = connect();
        client.request(registration("Amir"), RegisterResponse.class);

        assertFalse(client.request(login("Amir", "Wr0ng!pass", false), LoginResponse.class).ok());
        assertFalse(client.request(login("nobody", GOOD_PASSWORD, false), LoginResponse.class).ok());
        assertEquals(0, server.onlineCount());
    }

    @Test
    @DisplayName("only one account at a time is the stay-signed-in one")
    void stayLoggedInIsExclusive() throws Exception {
        TestClient amir = connect();
        amir.request(registration("Amir"), RegisterResponse.class);
        amir.request(login("Amir", GOOD_PASSWORD, true), LoginResponse.class);

        TestClient parsa = connect();
        parsa.request(registration("Parsa"), RegisterResponse.class);
        parsa.request(login("Parsa", GOOD_PASSWORD, true), LoginResponse.class);

        assertEquals("Parsa", DatabaseManager.getInstance().getLoggedInUser().getUsername());
        assertFalse(DatabaseManager.getInstance().findUser("Amir").isStayLoggedIn());
    }

    @Test
    @DisplayName("signing out clears the auto-login flag and leaves the online list")
    void logoutClearsEverything() throws Exception {
        TestClient client = connect();
        client.request(registration("Amir"), RegisterResponse.class);
        client.request(login("Amir", GOOD_PASSWORD, true), LoginResponse.class);

        assertTrue(client.request(new LogoutRequest(), AckResponse.class).ok());

        assertEquals(0, server.onlineCount());
        assertNull(DatabaseManager.getInstance().getLoggedInUser());
    }

    // ---- the cross-device requirement ------------------------------------------------------------

    @Test
    @DisplayName("progress follows the account, not the machine")
    void progressIsFetchedFromTheServerOnLogin() throws Exception {
        // The spec, near enough verbatim: "if a user logs into their account with another device, they
        // must be able to view their number of gems, coins, and other account-related information".
        //
        // Two separate connections stand in for two devices. Nothing about the first machine is
        // available to the second -- the only thing they share is the server.
        TestClient firstDevice = connect();
        firstDevice.request(registration("Amir"), RegisterResponse.class);
        LoginResponse first = firstDevice.request(login("Amir", GOOD_PASSWORD, false),
                LoginResponse.class);

        // Play a bit: earn coins and gems, then save -- which on a real client is RemoteBackend.flush()
        // firing from any one of the 23 existing saveAll() call sites.
        var profile = first.user().toUser().getProfile();
        profile.setCoins(8400);
        profile.setGems(120);
        profile.recordScoringGameRun(1500);
        var carrier = first.user().toUser();
        carrier.setProfile(profile);
        assertTrue(firstDevice.request(new ProfileSyncRequest(UserRecord.from(carrier)),
                ProfileSyncResponse.class).ok());
        firstDevice.close();

        // A different device, a fresh connection, nothing local.
        TestClient secondDevice = connect();
        LoginResponse second = secondDevice.request(login("Amir", GOOD_PASSWORD, false),
                LoginResponse.class);

        var restored = second.user().toUser().getProfile();
        assertEquals(8400, restored.getCoins());
        assertEquals(120, restored.getGems());
        assertEquals(1500, restored.getBestNumberOfMeowPoints());
    }

    @Test
    @DisplayName("a profile sync can never touch credentials or identity")
    void profileSyncCannotChangeCredentials() throws Exception {
        TestClient client = connect();
        client.request(registration("Amir"), RegisterResponse.class);
        client.request(login("Amir", GOOD_PASSWORD, false), LoginResponse.class);

        String originalHash = DatabaseManager.getInstance().findUser("Amir").getHashPassword();

        // A second account, so there is somebody real to try to overwrite.
        TestClient other = connect();
        other.request(registration("Parsa"), RegisterResponse.class);
        int parsaCoins = DatabaseManager.getInstance().findUser("Parsa").getProfile().getCoins();

        // A hostile (or merely buggy) client: the record claims to be Parsa, carries a password hash
        // the attacker knows, and asks for a fortune. All three claims are ignored -- not refused,
        // IGNORED -- because the handler reads only the profile half and writes only to the account
        // this connection signed in as. The packet never gets to name an account or a credential.
        User forged = new User("Parsa", "Parsa", "p@example.com", Gender.MALE,
                PasswordHasher.hash("attacker-knows-this"), 0, SecurityAnswer.hash("guess"));
        forged.getProfile().setCoins(999999);
        client.request(new ProfileSyncRequest(UserRecord.from(forged)), ProfileSyncResponse.class);

        // Amir's own credentials and identity: untouched.
        assertEquals(originalHash, DatabaseManager.getInstance().findUser("Amir").getHashPassword());
        assertEquals("Amir", DatabaseManager.getInstance().findUser("Amir").getUsername());
        // Parsa: entirely untouched, credentials and coins alike.
        assertNotEquals(PasswordHasher.hash("attacker-knows-this"),
                DatabaseManager.getInstance().findUser("Parsa").getHashPassword());
        assertEquals(parsaCoins,
                DatabaseManager.getInstance().findUser("Parsa").getProfile().getCoins());
        // The coins DID land -- on Amir, the account that actually sent them. The sync still works;
        // it just cannot be aimed.
        assertEquals(999999, DatabaseManager.getInstance().findUser("Amir").getProfile().getCoins());
    }

    @Test
    @DisplayName("a profile sync before signing in is refused by the gate, not by the handler")
    void profileSyncNeedsAuthentication() throws Exception {
        TestClient client = connect();
        var reply = client.requestRaw(new ProfileSyncRequest(null));

        assertEquals(PacketType.ACK, reply.type(), "the auth gate answers, not the sync handler");
        assertFalse(reply.as(AckResponse.class).ok());
    }

    // ---- changing your password -------------------------------------------------------------------

    @Test
    @DisplayName("changing a password proves the old one, server-side")
    void passwordChangeRequiresTheOldPassword() throws Exception {
        TestClient client = connect();
        client.request(registration("Amir"), RegisterResponse.class);
        client.request(login("Amir", GOOD_PASSWORD, false), LoginResponse.class);

        // Wrong current password: refused, and nothing changes. Checking this on the client would be
        // theatre -- it would be comparing against a hash the client already holds.
        AckResponse wrong = client.request(new PasswordChangeRequest(
                PasswordHasher.hash("not-it"), PasswordHasher.hash("An0ther!pass")),
                AckResponse.class);
        assertFalse(wrong.ok());
        assertTrue(client.request(login("Amir", GOOD_PASSWORD, false), LoginResponse.class).ok(),
                "the old password must still work after a refused change");

        AckResponse ok = client.request(new PasswordChangeRequest(
                PasswordHasher.hash(GOOD_PASSWORD), PasswordHasher.hash("An0ther!pass")),
                AckResponse.class);
        assertTrue(ok.ok(), ok.message());

        assertFalse(client.request(login("Amir", GOOD_PASSWORD, false), LoginResponse.class).ok());
        assertTrue(client.request(login("Amir", "An0ther!pass", false), LoginResponse.class).ok());
    }

    @Test
    @DisplayName("a password change survives a reconnect -- it is not lost like a profile-only sync")
    void passwordChangePersists() throws Exception {
        // The bug this guards: password changes used to mutate the local User and rely on saveAll(),
        // and a networked save carries only the profile. The change appeared to work and was gone at
        // the next login.
        TestClient client = connect();
        client.request(registration("Amir"), RegisterResponse.class);
        client.request(login("Amir", GOOD_PASSWORD, false), LoginResponse.class);
        client.request(new PasswordChangeRequest(PasswordHasher.hash(GOOD_PASSWORD),
                PasswordHasher.hash("An0ther!pass")), AckResponse.class);
        client.close();

        TestClient fresh = connect();
        assertTrue(fresh.request(login("Amir", "An0ther!pass", false), LoginResponse.class).ok(),
                "the new password must survive a completely new connection");
    }

    // ---- the leaderboard --------------------------------------------------------------------------

    @Test
    @DisplayName("the leaderboard is served from the server's roster, with everybody on it")
    void leaderboardComesFromTheServer() throws Exception {
        TestClient amir = connect();
        amir.request(registration("Amir"), RegisterResponse.class);
        LoginResponse amirLogin = amir.request(login("Amir", GOOD_PASSWORD, false),
                LoginResponse.class);
        User amirUser = amirLogin.user().toUser();
        amirUser.getProfile().recordScoringGameRun(900);
        amir.request(new ProfileSyncRequest(UserRecord.from(amirUser)), ProfileSyncResponse.class);

        TestClient parsa = connect();
        parsa.request(registration("Parsa"), RegisterResponse.class);
        LoginResponse parsaLogin = parsa.request(login("Parsa", GOOD_PASSWORD, false),
                LoginResponse.class);
        User parsaUser = parsaLogin.user().toUser();
        parsaUser.getProfile().recordScoringGameRun(1500);
        parsa.request(new ProfileSyncRequest(UserRecord.from(parsaUser)), ProfileSyncResponse.class);

        // Amir asks, and sees BOTH players -- which is the whole point. A client's own roster holds
        // only itself, so a board built locally would have exactly one row on it.
        LeaderboardResponse board = amir.request(new LeaderboardRequest("MEOW_POINT", false),
                LeaderboardResponse.class);

        assertEquals(2, board.rows().size());
        assertEquals("Parsa", board.rows().get(0).getUsername(), "highest score first");
        assertEquals(1500, board.rows().get(0).getBestMeowPoint());
        assertEquals("Amir", board.rows().get(1).getUsername());
        assertEquals(2, board.yourRank(), "Amir asked, so Amir's rank comes back");
    }

    @Test
    @DisplayName("a player who has never played the scoring game shows no score and ranks last")
    void neverPlayedRanksLastOnTheServerBoard() throws Exception {
        TestClient ace = connect();
        ace.request(registration("Ace"), RegisterResponse.class);
        LoginResponse aceLogin = ace.request(login("Ace", GOOD_PASSWORD, false), LoginResponse.class);
        User aceUser = aceLogin.user().toUser();
        aceUser.getProfile().recordScoringGameRun(10);
        ace.request(new ProfileSyncRequest(UserRecord.from(aceUser)), ProfileSyncResponse.class);

        TestClient rookie = connect();
        rookie.request(registration("Rookie"), RegisterResponse.class);
        rookie.request(login("Rookie", GOOD_PASSWORD, false), LoginResponse.class);

        LeaderboardResponse board = rookie.request(new LeaderboardRequest("MEOW_POINT", false),
                LeaderboardResponse.class);

        assertEquals("Ace", board.rows().get(0).getUsername());
        // The spec's requirement, end to end: a null crosses the wire as a null and renders as "-",
        // never as a 0 the player never earned.
        assertNull(board.rows().get(1).getBestMeowPoint());
        assertEquals("-", board.rows().get(1).getMeowPointLabel());
    }

    @Test
    @DisplayName("an unknown sort column falls back to the default instead of failing the request")
    void unknownColumnFallsBack() throws Exception {
        TestClient client = connect();
        client.request(registration("Amir"), RegisterResponse.class);
        client.request(login("Amir", GOOD_PASSWORD, false), LoginResponse.class);

        // A client on a newer build asking for a column this server has never heard of should still
        // get a board rather than an error it cannot act on.
        LeaderboardResponse board = client.request(new LeaderboardRequest("from-the-future", false),
                LeaderboardResponse.class);
        assertEquals(1, board.rows().size());
    }

    @Test
    @DisplayName("the leaderboard is not readable before signing in")
    void leaderboardNeedsAuthentication() throws Exception {
        TestClient client = connect();
        var reply = client.requestRaw(new LeaderboardRequest("MEOW_POINT", false));
        assertEquals(PacketType.ACK, reply.type());
        assertFalse(reply.as(AckResponse.class).ok());
    }

    // ---- password recovery ------------------------------------------------------------------------

    @Test
    @DisplayName("the security question can be looked up and the password reset with the answer")
    void recoveryRoundTrip() throws Exception {
        TestClient client = connect();
        client.request(registration("Amir"), RegisterResponse.class);

        RecoveryQuestionResponse question = client.request(new RecoveryQuestionRequest("Amir", "gardener@example.com"),
                RecoveryQuestionResponse.class);
        assertTrue(question.ok());
        assertEquals(0, question.securityQuestionIndex());

        // The client normalises through SecurityAnswer BEFORE hashing. It has to: the server never
        // sees the typed answer, so it cannot normalise on the player's behalf, and "Fluffy " hashed
        // raw would never match "fluffy".
        AckResponse reset = client.request(new RecoverySubmitRequest("Amir",
                SecurityAnswer.hash("  FLUFFY "), PasswordHasher.hash("N3w!password")),
                AckResponse.class);
        assertTrue(reset.ok(), reset.message());

        assertFalse(client.request(login("Amir", GOOD_PASSWORD, false), LoginResponse.class).ok());
        assertTrue(client.request(login("Amir", "N3w!password", false), LoginResponse.class).ok());
    }

    @Test
    @DisplayName("a wrong security answer does not change the password")
    void wrongRecoveryAnswerChangesNothing() throws Exception {
        TestClient client = connect();
        client.request(registration("Amir"), RegisterResponse.class);
        String originalHash = DatabaseManager.getInstance().findUser("Amir").getHashPassword();

        AckResponse reset = client.request(new RecoverySubmitRequest("Amir",
                SecurityAnswer.hash("not it"), PasswordHasher.hash("N3w!password")),
                AckResponse.class);

        assertFalse(reset.ok());
        assertEquals(originalHash, DatabaseManager.getInstance().findUser("Amir").getHashPassword());
        assertNotEquals(PasswordHasher.hash("N3w!password"), originalHash);
    }

    // ---- the scoring game's record ---------------------------------------------------------------

    @Test
    @DisplayName("a first score is kept, and the server says it was a first")
    void firstScoreIsKept() throws Exception {
        TestClient client = playing("amir");

        net.packets.ScoreSubmitResponse response = client.request(
                new net.packets.ScoreSubmitRequest(740), net.packets.ScoreSubmitResponse.class);

        assertTrue(response.accepted());
        assertEquals(740, response.newBest());
        // Null, not zero. The spec is explicit that somebody who has never played must not show a
        // score, and zero is a score a player can actually get.
        assertNull(response.previousBest(), "a first run has no previous best");
        assertEquals(740, DatabaseManager.getInstance().findUser("amir")
                .getProfile().getBestNumberOfMeowPoints());
    }

    @Test
    @DisplayName("a record only ever moves upward")
    void onlyBetterScoresAreKept() throws Exception {
        TestClient client = playing("amir");
        client.request(new net.packets.ScoreSubmitRequest(740),
                net.packets.ScoreSubmitResponse.class);

        net.packets.ScoreSubmitResponse worse = client.request(
                new net.packets.ScoreSubmitRequest(200), net.packets.ScoreSubmitResponse.class);
        assertFalse(worse.accepted());
        assertEquals(740, worse.newBest(), "the record stands");
        assertEquals(740, worse.previousBest());

        net.packets.ScoreSubmitResponse better = client.request(
                new net.packets.ScoreSubmitRequest(900), net.packets.ScoreSubmitResponse.class);
        assertTrue(better.accepted());
        assertEquals(900, better.newBest());
        assertEquals(740, better.previousBest());
    }

    @Test
    @DisplayName("a profile sync cannot lower somebody's score")
    void profileSyncCannotLowerTheRecord() throws Exception {
        TestClient client = playing("amir");
        client.request(new net.packets.ScoreSubmitRequest(740),
                net.packets.ScoreSubmitResponse.class);

        // Sync replaces the profile wholesale, which is right for coins and settings and wrong for a
        // record: the leaderboard everybody else reads is built from this field, so a client that
        // could write any value into it would be writing the leaderboard.
        //
        // The tampered value is put into a SNAPSHOT and the live object is put back, because client
        // and server share one JVM here and findUser hands out the server's own User -- mutating it
        // and leaving it mutated would change what the server believes and test nothing at all.
        User user = DatabaseManager.getInstance().findUser("amir");
        user.getProfile().setBestNumberOfMeowPoints(5);
        UserRecord tampered = UserRecord.from(user);
        user.getProfile().setBestNumberOfMeowPoints(740);

        ProfileSyncResponse synced = client.request(new ProfileSyncRequest(tampered),
                ProfileSyncResponse.class);

        assertTrue(synced.ok());
        assertEquals(740, synced.user().getProfile().toProfile().getBestNumberOfMeowPoints(),
                "the server keeps its record and echoes what it kept");
        assertEquals(740, DatabaseManager.getInstance().findUser("amir")
                .getProfile().getBestNumberOfMeowPoints());
    }

    @Test
    @DisplayName("a negative score is refused rather than becoming a record")
    void negativeScoresAreRefused() throws Exception {
        TestClient client = playing("amir");
        net.packets.ScoreSubmitResponse response = client.request(
                new net.packets.ScoreSubmitRequest(-5), net.packets.ScoreSubmitResponse.class);

        assertFalse(response.accepted());
        // Nothing in the rulebook can produce a negative total, so this is a bug or a probe -- and
        // either way it must not end up on the board.
        assertNull(DatabaseManager.getInstance().findUser("amir")
                .getProfile().getBestNumberOfMeowPoints());
    }

    @Test
    @DisplayName("a stranger cannot submit a score")
    void submittingNeedsAuthentication() throws Exception {
        TestClient stranger = connect();
        var reply = stranger.requestRaw(new net.packets.ScoreSubmitRequest(9999));
        assertEquals(PacketType.ACK, reply.type());
        assertFalse(reply.as(AckResponse.class).ok());
    }

    // ---- helpers --------------------------------------------------------------------------------

    // Registered and signed in, which is what every scoring submission needs behind it.
    private TestClient playing(String username) throws Exception {
        TestClient client = connect();
        assertTrue(client.request(registration(username), RegisterResponse.class).ok());
        assertTrue(client.request(login(username, GOOD_PASSWORD, false), LoginResponse.class).ok());
        return client;
    }

    private TestClient connect() throws IOException, InterruptedException {
        TestClient client = TestClient.connected(server.port());
        clients.add(client);
        return client;
    }

    // A well-formed sign-up, hashed exactly as the real client hashes it: the password through
    // PasswordHasher, the security answer through SecurityAnswer (never PasswordHasher directly --
    // recovery verifies against SecurityAnswer's canonical form, and getting that wrong is what made
    // every recovery attempt fail once before).
    private static RegisterRequest registration(String username) {
        return new RegisterRequest(username, PasswordHasher.hash(GOOD_PASSWORD), "Gardener",
                "gardener@example.com", "MALE", 0, SecurityAnswer.hash("fluffy"));
    }

    private static LoginRequest login(String username, String plaintext, boolean stay) {
        return new LoginRequest(username, PasswordHasher.hash(plaintext), stay);
    }
}
