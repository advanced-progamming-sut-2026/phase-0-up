package utils.validation;

import utils.Result;
import utils.regex.SignUpMenuRegex;

public class EmailValidator implements Validator{
    @Override
    public Result validate(String value) {
        if (!SignUpMenuRegex.EMAIL.matches(value)) return new Result(false, "Invalid email address");

        return new Result(true, "");
    }
}
