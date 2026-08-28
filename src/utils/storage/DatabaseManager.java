package utils.storage;

import models.user.User;

// The one door to the account roster, for the whole game.
//
// ## What this class is now
//
// It used to BE the storage: a HashMap, a JSON file, and the save-file repair logic. All of that has
// moved to LocalFileBackend, and this is a facade in front of an AccountBackend.
//
// That indirection is the entire Phase 3 storage migration. There are 23 calls to saveAll() spread
// across 20 files -- every command that changes a profile, plus GameEngine's end-of-level save -- and
// none of them changed. They still call saveAll(); what saveAll DOES is chosen once, by the process's
// composition root:
//
//   Main            LocalFileBackend  -- the terminal build stays fully playable with no server
//   ServerMain      LocalFileBackend  -- pointed at the server's own roster file
//   PvZGame         RemoteBackend     -- every operation becomes a packet
//
// Rewriting those 23 call sites instead would have been 23 chances to miss one, and a missed one is a
// player's progress silently staying on their own machine while they believe it is synced.
//
// ## Installing a backend
//
// setBackend() must be called BEFORE the first getInstance(), or the default local backend is built
// first and reads a save file the process may have no business reading. Both composition roots do this
// as their first storage-related act.
public class DatabaseManager {

    private static DatabaseManager instance;

    private volatile AccountBackend backend;

    private DatabaseManager(AccountBackend backend) {
        this.backend = backend;
    }

    // Choose where accounts live. Call this before anything touches getInstance().
    public static synchronized void setBackend(AccountBackend backend) {
        if (backend == null) {
            throw new IllegalArgumentException("an account backend is required");
        }
        if (instance == null) {
            instance = new DatabaseManager(backend);
        } else {
            instance.backend = backend;
        }
    }

    // Defaults to the local save file, which is what every test and the terminal build expect. A
    // process that wants something else says so first; a process that says nothing gets the behaviour
    // this class has always had.
    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager(new LocalFileBackend());
        }
        return instance;
    }

    public AccountBackend backend() {
        return backend;
    }

    // Only for tests, which need a clean singleton between cases. Not a general-purpose reset: the
    // running game holds User references that survive this, which is fine because the objects are the
    // same ones -- but nothing else should be doing that on purpose.
    static synchronized void resetForTesting() {
        instance = null;
    }

    // ---- credentials ----------------------------------------------------------------------------
    //
    // Verified by the backend, never by the caller. See AuthResult for why fetching an account to
    // compare its password stopped being an option the moment the roster moved off the machine.

    public AuthResult authenticate(String username, String passwordHash, boolean stayLoggedIn) {
        return backend.authenticate(username, passwordHash, stayLoggedIn);
    }

    public RecoveryStart beginRecovery(String username, String email) {
        return backend.beginRecovery(username, email);
    }

    public utils.Result verifyRecoveryAnswer(String username, String answerHash) {
        return backend.verifyRecoveryAnswer(username, answerHash);
    }

    public utils.Result completeRecovery(String username, String answerHash, String newPasswordHash) {
        return backend.completeRecovery(username, answerHash, newPasswordHash);
    }

    public utils.Result changePassword(String username, String currentPasswordHash,
                                       String newPasswordHash) {
        return backend.changePassword(username, currentPasswordHash, newPasswordHash);
    }

    // ---- the leaderboard ------------------------------------------------------------------------

    // The whole-game board, sorted. Asked of the backend rather than assembled from getAllUsers(),
    // because a networked client HAS no roster to assemble one from -- getAllUsers there is honestly
    // just the signed-in account, and building a leaderboard from it would show a board of one.
    //
    // What comes back is LeaderboardEntry rows: plain scalars, no credentials. That is what makes it
    // safe for the server to answer this for everybody when it will not answer findUser for anybody.
    public java.util.List<models.leaderboard.LeaderboardEntry> leaderboard(
            models.leaderboard.LbColumn column, boolean ascending) {
        return backend.leaderboard(column, ascending);
    }

    // A finished scoring-game run, offered to whoever keeps the record. Returns the best afterwards,
    // or null if that could not be established -- see AccountBackend.submitScore.
    public Integer submitScore(int meowPoints) {
        return backend.submitScore(meowPoints);
    }

    // ---- the roster -----------------------------------------------------------------------------

    public User findUser(String username) {
        return backend.findUser(username);
    }

    public boolean usernameExists(String username) {
        return backend.usernameExists(username);
    }

    public boolean addUser(User newUser) {
        return backend.addUser(newUser);
    }

    public boolean renameUser(String oldUsername, String newUsername) {
        return backend.renameUser(oldUsername, newUsername);
    }

    public boolean removeUser(String username) {
        return backend.removeUser(username);
    }

    // Every registered player, for whole-game views such as the leaderboard. Read-only, so callers can
    // iterate the roster without being able to mutate it.
    public java.util.Collection<User> getAllUsers() {
        return backend.getAllUsers();
    }

    public User getLoggedInUser() {
        return backend.getLoggedInUser();
    }

    // The name every caller in the game already uses. Kept exactly as it was -- this signature is the
    // reason 23 call sites did not have to change.
    public void saveAll() {
        backend.flush();
    }

    // Re-reading from storage was only ever meaningful for the file backend, and only at start-up,
    // which its constructor now does. Kept so no caller breaks, and deliberately a no-op otherwise.
    public void loadAll() {
        // Backends load themselves when they are built.
    }
}
