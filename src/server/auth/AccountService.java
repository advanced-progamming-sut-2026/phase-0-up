package server.auth;

import controllers.systems.LeaderboardSystem;
import models.leaderboard.LbColumn;
import models.leaderboard.LeaderboardEntry;
import models.user.Gender;
import models.user.Profile;
import models.user.User;
import net.Envelope;
import net.PacketType;
import net.packets.AckResponse;
import net.packets.LoginRequest;
import net.packets.LoginResponse;
import net.packets.LeaderboardRequest;
import net.packets.LeaderboardResponse;
import net.packets.PasswordChangeRequest;
import net.packets.ProfileSyncRequest;
import net.packets.ProfileSyncResponse;
import net.packets.RecoveryQuestionRequest;
import net.packets.RecoveryQuestionResponse;
import net.packets.RecoverySubmitRequest;
import net.packets.RegisterRequest;
import net.packets.RegisterResponse;
import net.packets.RenameRequest;
import net.packets.RenameResponse;
import net.packets.ScoreSubmitRequest;
import net.packets.ScoreSubmitResponse;
import net.packets.UsernameCheckRequest;
import net.packets.UsernameCheckResponse;
import server.AuthLevel;
import server.ClientSession;
import server.GameServer;
import server.PacketHandler;
import utils.Constants;
import utils.Result;
import utils.storage.DatabaseManager;
import utils.storage.RecoveryStart;
import utils.storage.records.ProfileRecord;
import utils.storage.records.UserRecord;
import utils.validation.EmailValidator;
import utils.validation.NicknameValidator;
import utils.validation.UsernameValidator;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

// Registration, sign-in, password recovery and profile sync -- server side.
//
// ## What moved here, and what did not
//
// The spec requires account data to live on the server, and username uniqueness to be checked globally.
// Both fall out of the roster simply BEING here: DatabaseManager.addUser already refuses a duplicate
// and already matches case-insensitively, so this class adds no uniqueness rule of its own. It reuses
// UsernameValidator for the same reason -- that class already knows the allowed characters and already
// consults the roster.
//
// ## What client-side hashing costs, stated plainly
//
// Passwords are hashed on the client so no plaintext crosses the wire. The consequence is that the
// server cannot check password STRENGTH: PasswordValidator works on the plaintext, and the server sees
// only 64 hex characters. Strength is therefore enforced client-side only, and a modified client could
// register a weak password for its own account.
//
// That is a real limitation, and it is the right trade at this scale: the alternative is sending
// plaintext passwords over an unencrypted socket so the server can grade them, which is worse in a way
// that affects every honest player rather than only a dishonest one. What the server DOES enforce is
// everything that protects other people -- username uniqueness, username format, and the fact that a
// profile sync can never touch credentials.
public final class AccountService {

    // A SHA-256 hex digest, which is what PasswordHasher and SecurityAnswer both produce. Checked so a
    // client cannot store a plaintext password in the hash field and have the server keep it forever.
    private static final int HASH_LENGTH = 64;

    private final GameServer server;

    public AccountService(GameServer server) {
        this.server = server;
    }

    // Everything this service answers. The four pre-sign-in packets are the ONLY things a stranger can
    // send -- see AuthLevel, where the absence of a default is deliberate.
    public void registerHandlers() {
        register(PacketType.REGISTER_REQ, AuthLevel.ANONYMOUS, this::onRegister);
        register(PacketType.LOGIN_REQ, AuthLevel.ANONYMOUS, this::onLogin);
        register(PacketType.USERNAME_CHECK_REQ, AuthLevel.ANONYMOUS, this::onUsernameCheck);
        register(PacketType.RECOVERY_Q_REQ, AuthLevel.ANONYMOUS, this::onRecoveryQuestion);
        register(PacketType.RECOVERY_SUBMIT_REQ, AuthLevel.ANONYMOUS, this::onRecoverySubmit);

        register(PacketType.LOGOUT_REQ, AuthLevel.AUTHENTICATED, this::onLogout);
        register(PacketType.PROFILE_SYNC_REQ, AuthLevel.AUTHENTICATED, this::onProfileSync);
        register(PacketType.RENAME_REQ, AuthLevel.AUTHENTICATED, this::onRename);
        register(PacketType.PASSWORD_CHANGE_REQ, AuthLevel.AUTHENTICATED, this::onPasswordChange);
        register(PacketType.LEADERBOARD_REQ, AuthLevel.AUTHENTICATED, this::onLeaderboard);
        register(PacketType.SCORE_SUBMIT_REQ, AuthLevel.AUTHENTICATED, this::onScoreSubmit);
    }

    // The account changed is the one this CONNECTION signed in as -- the packet carries no username,
    // so a client cannot aim a password change at anybody. The old password is proved here, next to
    // the stored hash, which is the only place proving it means anything.
    private void onPasswordChange(ClientSession session, Envelope envelope) {
        PasswordChangeRequest request = envelope.as(PasswordChangeRequest.class);
        if (!looksHashed(request.newPasswordHash())) {
            session.reply(envelope, new AckResponse(false,
                    "That password did not arrive hashed. Update your game."));
            return;
        }
        Result result = database().changePassword(session.username(),
                request.currentPasswordHash(), request.newPasswordHash());
        session.reply(envelope, new AckResponse(result.success(), result.message()));
    }

    // A finished scoring-game run.
    //
    // The server is authoritative about the BEST, not about the run: it cannot recompute a Meow Point
    // total it did not simulate, so a submitted score is taken at face value. What it does own is the
    // record -- the value only ever moves upward, and only ever for the account that is signed in on
    // this socket, so a client cannot lower somebody's best or write to a name that is not theirs.
    //
    // A negative score is refused outright. Nothing in the rulebook can produce one, so it is either a
    // bug or somebody probing, and either way it must not become a "best".
    private void onScoreSubmit(ClientSession session, Envelope envelope) {
        ScoreSubmitRequest request = envelope.as(ScoreSubmitRequest.class);
        Profile profile = session.user() == null ? null : session.user().getProfile();
        if (profile == null || request.meowPoints() < 0) {
            session.reply(envelope, new ScoreSubmitResponse(false, 0, null));
            return;
        }
        // Read BEFORE the update, and boxed: null means this is their first run ever, which is the one
        // thing the leaderboard's "My Point" column has to be able to tell apart from a score of zero.
        Integer previous = profile.getBestNumberOfMeowPoints();
        boolean accepted = profile.recordScoringGameRun(request.meowPoints());
        if (accepted) {
            database().saveAll();   // a best that is only in memory is a best lost to the next restart
        }
        session.reply(envelope, new ScoreSubmitResponse(accepted,
                profile.getBestNumberOfMeowPoints(), previous));
    }

    // The one field a profile sync may not lower.
    //
    // Sync replaces the profile wholesale, which is right for everything a player owns -- coins, gems,
    // unlocked plants, settings. It is wrong for a RECORD: a leaderboard column that any client can
    // write any value into is not a leaderboard, and two devices signed into one account would
    // otherwise race, with whichever synced last deciding the best score.
    //
    // So the stored record wins unless the incoming one beats it, and a real improvement still goes
    // through ScoreSubmitRequest, which answers with what the server actually kept.
    private static void keepMeowPointRecord(User user, Integer bestSoFar) {
        if (bestSoFar == null || user.getProfile() == null) {
            return;
        }
        Integer incoming = user.getProfile().getBestNumberOfMeowPoints();
        if (incoming == null || incoming < bestSoFar) {
            user.getProfile().setBestNumberOfMeowPoints(bestSoFar);
        }
    }

    // The whole board, from the server's own roster.
    //
    // This is the one request that returns other players' data, and the reason it is allowed to is what
    // it returns: LeaderboardEntry is a username and six scalars. Compare findUser, which the server
    // will not answer for anybody but the caller -- the difference is credentials, not privacy in
    // general.
    //
    // Gated to signed-in callers anyway. Not because the rows are sensitive, but because there is no
    // reason for a stranger who has not proved anything to be able to enumerate the player base.
    private void onLeaderboard(ClientSession session, Envelope envelope) {
        LeaderboardRequest request = envelope.as(LeaderboardRequest.class);
        LbColumn column = parseColumn(request.column());
        List<LeaderboardEntry> rows = LeaderboardSystem.getInstance()
                .sortBy(column, request.ascending(), database().getAllUsers());

        // Where the caller sits on the board they just asked for, in the order they asked for it.
        // Computed here rather than on the client, which would otherwise have to scan for its own
        // username and get the case-insensitivity wrong.
        int yourRank = 0;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).getUsername() != null
                    && rows.get(i).getUsername().equalsIgnoreCase(session.username())) {
                yourRank = i + 1;
                break;
            }
        }
        session.reply(envelope, new LeaderboardResponse(rows, yourRank));
    }

    // An unknown column name falls back to the default rather than failing the request. A client on a
    // newer build asking for a column this server does not have should still get a board.
    private static LbColumn parseColumn(String name) {
        LbColumn column = LbColumn.fromToken(name);
        return column == null ? LbColumn.MEOW_POINT : column;
    }

    private void register(PacketType type, AuthLevel level, PacketHandler handler) {
        server.register(type, level, handler);
    }

    private DatabaseManager database() {
        return DatabaseManager.getInstance();
    }

    // ---- registration ---------------------------------------------------------------------------

    private void onRegister(ClientSession session, Envelope envelope) {
        RegisterRequest request = envelope.as(RegisterRequest.class);

        String refusal = validateRegistration(request);
        if (refusal != null) {
            session.reply(envelope, new RegisterResponse(false, refusal, null));
            return;
        }

        Gender gender = parseGender(request.gender());
        User user = new User(request.username().trim(), request.nickname(), request.email(), gender,
                request.passwordHash(), request.securityQuestionIndex(), request.securityAnswerHash());

        // addUser is what actually enforces global uniqueness, and it does so atomically -- the
        // validator's check above is a nicer error message, not the guarantee. Two clients registering
        // the same name at the same instant both pass validation; only one gets past here.
        if (!database().addUser(user)) {
            session.reply(envelope, new RegisterResponse(false,
                    "'" + request.username() + "' was taken a moment ago. Pick another one.", null));
            return;
        }
        database().saveAll();

        // Deliberately NOT signed in. Registration has always dropped the player on the login menu,
        // and changing that here would make the two front ends disagree about what registering does.
        session.reply(envelope, new RegisterResponse(true, "User successfully registered",
                UserRecord.from(user)));
    }

    // Returns the refusal message, or null when the form is acceptable.
    private String validateRegistration(RegisterRequest request) {
        Result username = new UsernameValidator().validate(request.username());
        if (!username.success()) {
            return username.message();
        }
        Result nickname = new NicknameValidator().validate(request.nickname());
        if (!nickname.success()) {
            return nickname.message();
        }
        Result email = new EmailValidator().validate(request.email());
        if (!email.success()) {
            return email.message();
        }
        if (parseGender(request.gender()) == null) {
            return "Invalid gender";
        }
        if (!looksHashed(request.passwordHash())) {
            // Not a strength check -- the server cannot make one. This only refuses a client that
            // skipped hashing altogether, which would otherwise put a plaintext password in the roster
            // file permanently.
            return "That password did not arrive hashed. Update your game.";
        }
        if (!looksHashed(request.securityAnswerHash())) {
            return "That security answer did not arrive hashed. Update your game.";
        }
        // 0-based here; the terminal asks for 1..N and subtracts one before it ever gets this far.
        if (request.securityQuestionIndex() < 0
                || request.securityQuestionIndex() >= Constants.SECURITY_QUESTIONS.length) {
            return "Pick a question by number, 1 to " + Constants.SECURITY_QUESTIONS.length + ".";
        }
        return null;
    }

    private void onUsernameCheck(ClientSession session, Envelope envelope) {
        UsernameCheckRequest request = envelope.as(UsernameCheckRequest.class);
        session.reply(envelope, new UsernameCheckResponse(request.username(),
                database().usernameExists(request.username())));
    }

    // ---- signing in -----------------------------------------------------------------------------

    private void onLogin(ClientSession session, Envelope envelope) {
        LoginRequest request = envelope.as(LoginRequest.class);

        User user = database().findUser(request.username());
        if (user == null) {
            // The same sentence LoginCommand has always used, so the wording does not depend on which
            // build the player is running.
            session.reply(envelope, new LoginResponse(false, "User not found", null, null));
            return;
        }
        if (!hashesMatch(request.passwordHash(), user.getHashPassword())) {
            session.reply(envelope, new LoginResponse(false, "Wrong password", null, null));
            return;
        }

        applyStayLoggedIn(user, request.stayLoggedIn());
        database().saveAll();

        // Presence and authentication in one step -- attachAuthenticated is the only way a session
        // becomes signed in, precisely so a player can never be logged in and unreachable.
        server.attachAuthenticated(session, user);

        // The WHOLE account goes back, profile included. That single fact is the spec's cross-device
        // requirement: coins, gems and campaign progress arrive from the server on every login, so the
        // machine the player happens to be sitting at stops mattering.
        session.reply(envelope, new LoginResponse(true, "Welcome back! The lawn missed you.",
                UserRecord.from(user), session.issueToken(UUID.randomUUID().toString())));
    }

    // Only one account may be the auto-login one, so claiming it clears everybody else's flag.
    private void applyStayLoggedIn(User user, boolean stayLoggedIn) {
        if (!stayLoggedIn) {
            user.setStayLoggedIn(false);
            return;
        }
        for (User other : database().getAllUsers()) {
            other.setStayLoggedIn(false);
        }
        user.setStayLoggedIn(true);
    }

    private void onLogout(ClientSession session, Envelope envelope) {
        User user = session.user();
        if (user != null) {
            user.setStayLoggedIn(false);
            database().saveAll();
        }
        server.detachAuthenticated(session);
        session.reply(envelope, new AckResponse(true, "Signed out. The lawn will keep."));
    }

    // ---- password recovery ----------------------------------------------------------------------

    // Both handlers below delegate to the SAME backend methods the terminal build uses, so the account
    // rules -- the email check, the answer comparison, the re-check on reset -- are written once, in
    // LocalFileBackend, and cannot drift between the two builds.
    private void onRecoveryQuestion(ClientSession session, Envelope envelope) {
        RecoveryQuestionRequest request = envelope.as(RecoveryQuestionRequest.class);
        // The INDEX, not the question text: Constants.SECURITY_QUESTIONS is the one list, and both the
        // renderers and RecoveryStart already clamp an out-of-range index rather than indexing it raw.
        RecoveryStart start = database().beginRecovery(request.username(), request.email());
        session.reply(envelope, new RecoveryQuestionResponse(start.ok(), start.message(),
                start.securityQuestionIndex()));
    }

    private void onRecoverySubmit(ClientSession session, Envelope envelope) {
        RecoverySubmitRequest request = envelope.as(RecoverySubmitRequest.class);

        // Verify-only. The terminal conversation needs this so a wrong answer is reported before the
        // player is asked for a new password.
        if (request.isVerifyOnly()) {
            Result verified = database().verifyRecoveryAnswer(request.username(),
                    request.answerHash());
            session.reply(envelope, new AckResponse(verified.success(), verified.message()));
            return;
        }

        if (!looksHashed(request.newPasswordHash())) {
            session.reply(envelope, new AckResponse(false,
                    "That password did not arrive hashed. Update your game."));
            return;
        }
        // completeRecovery re-checks the answer itself. Nothing forces a client to have made the
        // verify call above, and a check that can be skipped by not calling it is not a check.
        //
        // One consequence worth naming: SecurityAnswer.wasLegacyMatch, which rescues answers stored by
        // an older build under a different normalisation, needs the raw text and therefore cannot run
        // over the wire at all. An account created before that fix can still recover through the
        // terminal build, which has the typed answer in hand.
        Result reset = database().completeRecovery(request.username(), request.answerHash(),
                request.newPasswordHash());
        session.reply(envelope, new AckResponse(reset.success(), reset.message()));
    }

    // ---- profile sync ---------------------------------------------------------------------------

    // "Here is my account as it now stands." Fired by the client's RemoteBackend whenever anything
    // calls DatabaseManager.saveAll() -- which is 23 places, none of which know they are on a network.
    private void onProfileSync(ClientSession session, Envelope envelope) {
        ProfileSyncRequest request = envelope.as(ProfileSyncRequest.class);
        User user = session.user();

        ProfileRecord incoming = request.user() == null ? null : request.user().getProfile();
        if (incoming == null) {
            session.reply(envelope, new ProfileSyncResponse(false, "Nothing to save.", null));
            return;
        }

        // The username in the packet is IGNORED, not trusted and not even compared as a filter: the
        // account written to is whichever one this connection signed in as. A client cannot name
        // somebody else's account here, because it never gets to name an account at all.
        Integer bestSoFar = user.getProfile() == null
                ? null : user.getProfile().getBestNumberOfMeowPoints();
        user.setProfile(incoming.toProfile());
        keepMeowPointRecord(user, bestSoFar);

        // Nickname and email ride along, because ProfileCommands edits them by mutating the User and
        // then calling saveAll() -- so without this, changing your nickname on the graphical build
        // would appear to work and be silently lost. Neither is a credential and neither can be aimed
        // at another account, so carrying them here costs nothing.
        //
        // The USERNAME and the PASSWORD HASH deliberately do NOT ride along. A rename has a rule only
        // the server can enforce (the name must be free) and can be refused, so it gets its own packet;
        // and letting a progress sync carry a password hash would make "save my coins" and "change my
        // password" the same operation, which is exactly the conflation worth avoiding.
        applyIfPresent(request.user(), user);
        database().saveAll();

        // Echoed back so the client can rebase rather than assume its write won -- which matters as
        // soon as the server starts clamping anything (a Meow Point best that did not beat the record).
        session.reply(envelope, new ProfileSyncResponse(true, null, UserRecord.from(user)));
    }

    private static void applyIfPresent(UserRecord incoming, User user) {
        if (incoming == null) {
            return;
        }
        if (incoming.getNickname() != null && !incoming.getNickname().isBlank()) {
            user.changeNickname(incoming.getNickname());
        }
        if (incoming.getEmail() != null && !incoming.getEmail().isBlank()) {
            user.changeEmail(incoming.getEmail());
        }
    }

    // ---- renaming -------------------------------------------------------------------------------

    private void onRename(ClientSession session, Envelope envelope) {
        RenameRequest request = envelope.as(RenameRequest.class);
        User user = session.user();
        String from = user.getUsername();

        // renameUser holds the uniqueness rule and re-keys the roster; it refuses a name that belongs
        // to somebody else. This is the reason a rename cannot ride along on a profile sync -- a sync
        // applies whatever it is given, and this can say no.
        if (!database().renameUser(from, request.newUsername())) {
            session.reply(envelope, new RenameResponse(false,
                    "'" + request.newUsername() + "' is already taken by another gardener.", null));
            return;
        }
        database().saveAll();

        // Presence is keyed by username, so the roster and the online list would now disagree about
        // who this connection is: a challenge addressed to the new name would find nobody, and the old
        // name would still resolve to this session. Re-attaching under the new name is what keeps the
        // two in step -- and it has to happen AFTER the rename, or it files them under the old key.
        server.detachAuthenticated(session);
        server.attachAuthenticated(session, user);

        session.reply(envelope, new RenameResponse(true, "Renamed. Say hello to " + user.getUsername()
                + ".", UserRecord.from(user)));
    }

    // ---- helpers --------------------------------------------------------------------------------

    private static Gender parseGender(String gender) {
        if (gender == null) {
            return null;
        }
        if (gender.equalsIgnoreCase("male")) {
            return Gender.MALE;
        }
        if (gender.equalsIgnoreCase("female")) {
            return Gender.FEMALE;
        }
        return null;
    }

    private static boolean looksHashed(String value) {
        if (value == null || value.length() != HASH_LENGTH) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!hex) {
                return false;
            }
        }
        return true;
    }

    // Constant-time, so the number of leading characters a guess got right cannot be read off how long
    // the answer took. String.equals returns early on the first mismatch, which over enough attempts
    // leaks the hash one character at a time.
    private static boolean hashesMatch(String offered, String stored) {
        if (offered == null || stored == null) {
            return false;
        }
        return MessageDigest.isEqual(offered.getBytes(StandardCharsets.UTF_8),
                stored.getBytes(StandardCharsets.UTF_8));
    }
}
