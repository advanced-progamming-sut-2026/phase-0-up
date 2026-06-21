package utils.validation;

import utils.Result;

public class NicknameValidator implements Validator{
    @Override
    public Result validate(String value) {
        if (value.length() < 3) return new Result(false, "Nickname should be at least 3 characters");
        if (value.length() > 30) return new Result(false, "Nickname should be at most 30 characters");

        return new Result(true, "");
    }
}
