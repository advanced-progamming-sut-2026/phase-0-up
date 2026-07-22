package utils.validation;

import utils.Result;
import utils.regex.SignUpMenuRegex;

import java.util.ArrayList;
import java.util.List;

public class PasswordValidator implements Validator {

    @Override
    public Result validate(String value) {
        if (value == null || value.isEmpty()) {
            return new Result(false, "Password can't be empty");
        }
        if (!SignUpMenuRegex.PASSWORD.matches(value)) {
            return new Result(false, "Invalid password format");
        }

        List<String> errors = new ArrayList<>();

        if (value.length() < 8) {
            errors.add("must be at least 8 characters long");
        }
        if (!value.matches(".*[a-z].*")) {
            errors.add("must contain a lowercase letter");
        }
        if (!value.matches(".*[A-Z].*")) {
            errors.add("must contain an uppercase letter");
        }
        if (!value.matches(".*[0-9].*")) {
            errors.add("must contain a number");
        }
        if (!value.matches(".*[\\p{Punct}].*")) {
            errors.add("must contain a special character");
        }

        if (!errors.isEmpty()) {
            return new Result(false, "Weak password: " + String.join(", ", errors));
        }

        return new Result(true, "");
    }
}
