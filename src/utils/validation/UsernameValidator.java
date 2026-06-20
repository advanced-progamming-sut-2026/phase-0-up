package utils.validation;

import utils.Result;
import utils.regex.SignUpMenuRegex;
import utils.storage.DatabaseManager;

public class UsernameValidator implements Validator{
    @Override
    public Result validate(String value) {
        if (!SignUpMenuRegex.USERNAME.matches(value))
            return new Result(false, "Invalid username");

        if (DatabaseManager.getInstance().usernameExists(value))
            return new Result(false, "Username already exists");

        return new Result(true, "");
    }
}
