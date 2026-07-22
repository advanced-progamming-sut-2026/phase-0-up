package utils.validation;

import utils.Result;
import utils.regex.SignUpMenuRegex;

public class EmailValidator implements Validator{
    @Override
    public Result validate(String value) {
        if (value == null || value.isBlank()) return new Result(false, "An email can't be empty.");
        if (!SignUpMenuRegex.EMAIL.matches(value.trim()))
            return new Result(false, "'" + value.trim() + "' doesn't look like an email address -- "
                    + "it should read like name@example.com.");

        return new Result(true, "");
    }
}
