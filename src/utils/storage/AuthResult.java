package utils.storage;

import models.user.User;

// The outcome of checking somebody's credentials.
//
// ## Why this type exists at all
//
// LoginCommand used to do this:
//
//     User user = DatabaseManager.getInstance().findUser(username);
//     if (user == null)                                        -> "User not found"
//     if (!PasswordHasher.matches(password, user.getHash()))   -> "Wrong password"
//
// Perfectly reasonable when the roster is a file on the same machine, and a credential leak the moment
// it is not: fetching a User to compare its password means the server would have to hand a full
// account -- password hash and security-answer hash included -- to an anonymous caller who has proved
// nothing. Anyone could ask for any username and receive its hashes.
//
// So the comparison moved to where the credentials live. The backend is asked to VERIFY, not to fetch,
// and a caller that fails gets a sentence rather than an account. The local backend does exactly what
// the command used to do inline, so the terminal build's behaviour is unchanged; the remote backend
// sends a LoginRequest and the hashes never leave the server.
public record AuthResult(User user, String message) {

    public boolean success() {
        return user != null;
    }

    public static AuthResult refused(String message) {
        return new AuthResult(null, message);
    }

    public static AuthResult of(User user, String message) {
        return new AuthResult(user, message);
    }
}
