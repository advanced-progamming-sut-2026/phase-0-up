package utils.validation;

import utils.Result;
import utils.regex.SignUpMenuRegex;

public class NicknameValidator implements Validator{
    @Override
    public Result validate(String value) {
        // A null or all-whitespace nickname used to reach length() and throw; it is simply invalid.
        if (value == null || value.isBlank()) return new Result(false, "A nickname can't be empty.");

        String candidate = value.trim();

        if (!SignUpMenuRegex.USERNAME.matches(candidate)){
            return new Result(false, "This nickname has invalid characters!");
        }
        if (candidate.length() < 3) return new Result(false, "Nickname should be at least 3 characters -- '"
                + candidate + "' is only " + candidate.length() + ".");
        if (candidate.length() > 30) return new Result(false, "Nickname should be at most 30 characters -- '"
                + candidate + "' is " + candidate.length() + ".");

        return new Result(true, "");
    }
}
