package views.net;

import models.leaderboard.LbColumn;
import models.leaderboard.LeaderboardEntry;
import models.user.User;
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
import net.packets.RenameRequest;
import net.packets.RenameResponse;
import net.packets.UsernameCheckRequest;
import net.packets.UsernameCheckResponse;
import utils.Result;
import utils.storage.AccountBackend;
import utils.storage.AuthResult;
import utils.storage.RecoveryStart;
import utils.storage.records.UserRecord;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

// Accounts, on the server.
//
// This is the whole Phase 3 storage migration, from the client's side. Every one of the 23 places in
// the game that calls DatabaseManager.saveAll() ends up here instead of at a file, and not one of them
// knows it: they call the same method with the same signature and get the same behaviour, only the
// bytes go to a socket.
//
// ## What this deliberately CANNOT do
//
// findUser answers only for the account this client is signed in as, and getAllUsers returns only that
// one. That is not a limitation to be fixed later -- it is the security property. A remote backend that
// could fetch arbitrary accounts would be a service that hands anyone a stranger's password hash for
// the asking, which is exactly why the credential checks moved behind authenticate() and
// beginRecovery() rather than being done by the caller on a fetched User.
//
// The leaderboard, which genuinely does need everybody's progress, gets it through its own request
// (T3.4) that returns leaderboard ROWS -- plain scalars, no credentials.
public final class RemoteBackend implements AccountBackend {

    private final NetClient net;

    // The signed-in account, held so findUser/flush have something to answer with. Assigned by
    // authenticate() and cleared by logout, which is the only way it ever changes.
    private volatile User currentUser;

    // Remembers the last "stay signed in" account so the next launch can resume it. See
    // StayLoggedInStore for why the credential lives on this machine and what that means.
    private final StayLoggedInStore stayLoggedIn;

    public RemoteBackend(NetClient net, StayLoggedInStore stayLoggedIn) {
        this.net = net;
        this.stayLoggedIn = stayLoggedIn;
    }

    public User currentUser() {
        return currentUser;
    }

    // ---- credentials ----------------------------------------------------------------------------

    @Override
    public AuthResult authenticate(String username, String passwordHash, boolean stay) {
        LoginResponse response = net.request(new LoginRequest(username, passwordHash, stay),
                LoginResponse.class);
        if (response == null) {
            return AuthResult.refused(offline());
        }
        if (!response.ok() || response.user() == null) {
            return AuthResult.refused(response.message());
        }

        // The record becomes a live User here, and this is where the account arrives from the server
        // on every login -- coins, gems, campaign progress and all. That is the spec's cross-device
        // requirement, and it needs no code of its own: LoginResponse carries the whole UserRecord.
        User user = response.user().toUser();
        currentUser = user;

        // Remembered only when asked for. Cleared otherwise, so unticking the box on one launch
        // actually forgets the previous launch's choice.
        if (stay) {
            stayLoggedIn.remember(user.getUsername(), passwordHash);
        } else {
            stayLoggedIn.forget();
        }
        return AuthResult.of(user, response.message());
    }

    @Override
    public RecoveryStart beginRecovery(String username, String email) {
        RecoveryQuestionResponse response = net.request(
                new RecoveryQuestionRequest(username, email), RecoveryQuestionResponse.class);
        if (response == null) {
            return RecoveryStart.refused(offline());
        }
        return response.ok()
                ? RecoveryStart.of(response.securityQuestionIndex())
                : RecoveryStart.refused(response.message());
    }

    @Override
    public Result verifyRecoveryAnswer(String username, String answerHash) {
        // A null new password is what makes this a verify rather than a reset -- one packet, two
        // phases. See RecoverySubmitRequest.
        return ack(net.request(new RecoverySubmitRequest(username, answerHash, null),
                AckResponse.class));
    }

    @Override
    public Result completeRecovery(String username, String answerHash, String newPasswordHash) {
        return ack(net.request(new RecoverySubmitRequest(username, answerHash, newPasswordHash),
                AckResponse.class));
    }

    @Override
    public Result changePassword(String username, String currentPasswordHash,
                                 String newPasswordHash) {
        // Its own packet, not part of a profile sync: the server has to prove the old password before
        // it will accept the new one, and a sync applies whatever it is given.
        return ack(net.request(
                new PasswordChangeRequest(currentPasswordHash, newPasswordHash), AckResponse.class));
    }

    // ---- the leaderboard ------------------------------------------------------------------------

    // The one request that legitimately returns other players' data -- and the reason it is safe is
    // what comes back: LeaderboardEntry rows are a username and six numbers. No credentials, no
    // profile internals, nothing that could be replayed as anybody.
    @Override
    public List<LeaderboardEntry> leaderboard(LbColumn column, boolean ascending) {
        LeaderboardResponse response = net.request(
                new LeaderboardRequest(column == null ? null : column.name(), ascending),
                LeaderboardResponse.class);
        if (response == null || response.rows() == null) {
            // An empty board, not a crash. The screen already has wording for "nobody here yet", and a
            // momentary connection blip should read as an empty board rather than take the menu down.
            return List.of();
        }
        return response.rows();
    }

    // ---- the roster -----------------------------------------------------------------------------

    // Only ever the signed-in account. See the class comment: anything else would be a credential
    // service, and nothing needs one -- the checks that used to fetch a User now verify server-side.
    @Override
    public User findUser(String username) {
        User user = currentUser;
        if (user == null || username == null || user.getUsername() == null) {
            return null;
        }
        return user.getUsername().equalsIgnoreCase(username.trim()) ? user : null;
    }

    @Override
    public boolean usernameExists(String username) {
        UsernameCheckResponse response = net.request(new UsernameCheckRequest(username),
                UsernameCheckResponse.class);
        // On a dead link, report the name as TAKEN. Registration then refuses instead of letting a
        // player fill in a whole form that was never going to reach anyone -- failing closed is the
        // kinder answer as well as the safer one.
        return response == null || response.taken();
    }

    @Override
    public boolean addUser(User newUser) {
        if (newUser == null) {
            return false;
        }
        // The hashes are already computed: RegisterCommand hashes the password and the security answer
        // before building this User, so nothing here needs the plaintext -- and there is none to leak.
        RegisterResponse response = net.request(new RegisterRequest(
                newUser.getUsername(),
                newUser.getHashPassword(),
                newUser.getNickname(),
                newUser.getEmail(),
                newUser.getGender() == null ? null : newUser.getGender().name(),
                newUser.getSecurityQuestionIndex(),
                newUser.getSecurityAnswerHash()), RegisterResponse.class);
        return response != null && response.ok();
    }

    @Override
    public boolean renameUser(String oldUsername, String newUsername) {
        RenameResponse response = net.request(new RenameRequest(newUsername), RenameResponse.class);
        if (response == null || !response.ok() || response.user() == null) {
            return false;
        }
        // The live User is renamed too. The caller holds the same instance the rest of the game does,
        // so leaving it on the old name would have the client and the server disagree about who this
        // player is until the next login.
        User user = currentUser;
        if (user != null) {
            user.changeUsername(response.user().getUsername());
        }
        stayLoggedIn.rename(response.user().getUsername());
        return true;
    }

    @Override
    public boolean removeUser(String username) {
        // Nothing in the game deletes an account, so there is no packet for it. Refusing is honest:
        // returning true would report a deletion that never happened.
        return false;
    }

    @Override
    public Collection<User> getAllUsers() {
        User user = currentUser;
        return user == null ? List.of() : Collections.singletonList(user);
    }

    // The stay-signed-in account, resumed by replaying the stored credential as an ordinary login.
    //
    // Called once at start-up by the composition root, before any screen exists.
    @Override
    public User getLoggedInUser() {
        StayLoggedInStore.Remembered remembered = stayLoggedIn.read();
        if (remembered == null) {
            return null;
        }
        AuthResult auth = authenticate(remembered.username(), remembered.passwordHash(), true);
        if (!auth.success()) {
            // The password changed, the account is gone, or the server is down. Forget it rather than
            // retrying the same failing credential on every launch forever.
            stayLoggedIn.forget();
            return null;
        }
        return auth.user();
    }

    // ---- persistence ----------------------------------------------------------------------------

    // What saveAll() became. The whole record goes up; the server applies the profile, the nickname and
    // the email, and ignores everything else -- see AccountService.onProfileSync for why the username
    // and the password hash are deliberately not carried here.
    @Override
    public void flush() {
        User user = currentUser;
        if (user == null) {
            return;   // signed out: there is nothing to save, and no account to save it to
        }
        ProfileSyncResponse response = net.request(new ProfileSyncRequest(UserRecord.from(user)),
                ProfileSyncResponse.class);
        if (response == null || !response.ok()) {
            // Reported rather than thrown. Every one of the 23 callers is mid-command -- a level has
            // just ended, a plant has just been bought -- and none of them can do anything useful with
            // an exception. GameEngine already catches a failing save and tells the player.
            System.err.println("[net] progress could not be saved: "
                    + (response == null ? net.lastError() : response.message()));
        }
    }

    // Signing out. Not part of AccountBackend -- LogoutCommand clears the session itself, and this is
    // the network half the composition root wires to it.
    public void signOut() {
        if (currentUser != null) {
            net.request(new LogoutRequest(), AckResponse.class);
        }
        currentUser = null;
        stayLoggedIn.forget();
    }

    private static Result ack(AckResponse response) {
        return response == null
                ? new Result(false, "The server is not answering. Check your connection.")
                : new Result(response.ok(), response.message());
    }

    private String offline() {
        String error = net.lastError();
        return error == null ? "The server is not answering. Check your connection." : error;
    }
}
