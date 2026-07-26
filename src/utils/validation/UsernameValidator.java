package utils.validation;

import utils.Result;
import utils.regex.SignUpMenuRegex;
import utils.storage.DatabaseManager;

public class UsernameValidator implements Validator{

    // The account allowed to already own this username -- set when validating a rename, so a player
    // re-casing their own name ("amir" -> "Amir") isn't told the name is taken by themselves.
    private final String currentUsername;

    public UsernameValidator() {
        this(null);
    }

    public UsernameValidator(String currentUsername) {
        this.currentUsername = currentUsername;
    }

    // Each failure says which rule was broken and which name broke it. A single catch-all message
    // ("invalid username") is unactionable: taken, mistyped and empty look identical to the player.
    @Override
    public Result validate(String value) {
        if (value == null || value.isBlank())
            return new Result(false, "A username can't be empty -- give me something for the mailbox.");

        String candidate = value.trim();

        if (!SignUpMenuRegex.USERNAME.matches(candidate))
            return new Result(false, "Usernames can only use letters, numbers and dashes -- '"
                    + candidate + "' has something else in it.");

        // usernameExists ignores case, so "Amir" can't be re-registered as "amir".
        if (DatabaseManager.getInstance().usernameExists(candidate)
                && !candidate.equalsIgnoreCase(currentUsername))
            return new Result(false, "'" + candidate + "' is already taken by another gardener "
                    + "(names match no matter the capitals). Pick a different one.");

        return new Result(true, "");
    }
}
