package utils.storage;

import models.user.User;

import java.util.Collection;

// Where accounts actually live.
//
// ## Why this interface exists
//
// Phase 3 moves account storage off the player's machine and onto the server. The naive way to do that
// is to find every place the game saves and make it send a packet instead -- and there are 23 calls to
// DatabaseManager.saveAll() across 20 files: login, registration, logout, password recovery, unlocking
// and upgrading plants, three greenhouse commands, reading news, the cheat menu, changing difficulty,
// editing a profile, boosting a seed, buying from the shop, GameEngine's end-of-level save,
// InputRouter, and QuestSystem. Rewriting all of them would be 23 chances to miss one, and a missed
// one is a player's progress silently staying local.
//
// So none of them change. They keep calling saveAll(); what saveAll DOES is what changes, and it
// changes in exactly one place -- here.
//
//   LocalFileBackend   the server, and the terminal build (which stays fully playable offline, because
//                      `gradlew run` is this project's regression harness and must not need a server)
//   RemoteBackend      the graphical client, where every method is a packet
//
// ## Implementations must be thread-safe
//
// The server calls this from connection reader threads and from match threads at the same time. The
// original DatabaseManager was a plain HashMap behind a singleton, which was fine when one terminal
// owned it and is not fine now.
public interface AccountBackend {

    // ---- credentials ----------------------------------------------------------------------------
    //
    // These four VERIFY rather than fetch, and that distinction is the whole reason they exist.
    // Checking a password used to mean fetching the account and comparing locally, which over a
    // network would have the server hand an anonymous caller a full account -- password hash and
    // security-answer hash included -- for any username they cared to name. The comparison belongs
    // where the secret lives; a caller that fails gets a sentence, never an account.

    // Verify a password and sign in. passwordHash is already hashed by the caller (PasswordHasher),
    // so no plaintext reaches an implementation that might send it anywhere.
    AuthResult authenticate(String username, String passwordHash, boolean stayLoggedIn);

    // Step one of recovery: confirm the account and email, and say which security question it uses.
    RecoveryStart beginRecovery(String username, String email);

    // Step two: is this the right answer? Separate from the reset so a front end can say "wrong
    // answer" before asking for a new password -- which is what the terminal conversation does, and
    // collapsing the two would make it ask for a password it was never going to accept.
    utils.Result verifyRecoveryAnswer(String username, String answerHash);

    // Step three: set the new password, re-checking the answer so the two steps cannot be separated by
    // a client that simply skips the verify call.
    utils.Result completeRecovery(String username, String answerHash, String newPasswordHash);

    // Change a password from the profile menu, proving the old one first.
    //
    // Separate from a profile sync, and from recovery, because it has its own rule: the caller must
    // already know the current password. Verifying that on the client would be theatre -- the check
    // has to happen next to the stored hash or it is not a check at all.
    utils.Result changePassword(String username, String currentPasswordHash, String newPasswordHash);

    // ---- the roster -----------------------------------------------------------------------------

    // Case-insensitive, always: "Amir" and "amir" are the same gardener, and every implementation has
    // to agree about that or the two halves of the system disagree about who exists.
    //
    // NOTE: a remote implementation answers this only for the account the connection is signed in as.
    // It is not a lookup service for other people's accounts, and never was meant to be one -- the
    // credential methods above exist precisely so nothing needs it to be.
    User findUser(String username);

    boolean usernameExists(String username);

    // Returns false when the name is already taken, rather than overwriting -- which used to wipe a
    // player's whole profile if a duplicate ever slipped past validation.
    boolean addUser(User newUser);

    // Returns false when the account is unknown, or the new name belongs to somebody else.
    boolean renameUser(String oldUsername, String newUsername);

    boolean removeUser(String username);

    // Every registered player, for whole-game views such as the leaderboard.
    Collection<User> getAllUsers();

    // The stay-signed-in account, or null. Consulted once at start-up by both composition roots.
    User getLoggedInUser();

    // Persist. What DatabaseManager.saveAll() delegates to, and therefore what all 23 call sites end
    // up invoking without knowing which of the two answers it.
    void flush();

    // ---- the leaderboard ------------------------------------------------------------------------

    // The whole-game board, sorted, as plain rows.
    //
    // It lives on this interface rather than being assembled from getAllUsers() by the caller, because
    // a networked client has no roster to assemble one from -- getAllUsers there is honestly just the
    // signed-in account. A leaderboard built from it would show a board of one, and it would look like
    // a rendering bug rather than a missing request.
    //
    // Safe to answer for everybody, unlike findUser, precisely because a LeaderboardEntry is plain
    // scalar progress: a username and six numbers, no credentials anywhere near it.
    java.util.List<models.leaderboard.LeaderboardEntry> leaderboard(
            models.leaderboard.LbColumn column, boolean ascending);
}
