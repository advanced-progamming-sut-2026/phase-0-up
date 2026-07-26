package utils.regex;

public enum SignUpMenuRegex implements Regex {
    SIGN_UP("^\\s*register\\s+-u\\s+(?<username>\\S+)\\s+-p\\s+(?<password>\\S+)\\s+(?<passwordConfirm>\\S+)" +
            "\\s+-n\\s+(?<nickname>\\S+)\\s+-e\\s+(?<email>\\S+)\\s+-g\\s+(?<gender>\\S+)\\s*$"),
    EMAIL("^(?!.*\\.\\.)[a-zA-Z0-9](?:[a-zA-Z0-9.\\-_]*[a-zA-Z0-9])?@" +
            "[a-zA-Z0-9\\-]+(?:\\.[a-zA-Z0-9\\-]+)*\\.[a-zA-Z]{2,}$"),
    USERNAME("^[a-zA-Z0-9\\-]+$"),
    PASSWORD("^[a-zA-Z0-9\\p{Punct}]+$"),
    // The answer groups accept a quoted phrase as well as a bare word, mirroring
    // LoginMenuRegex.ANSWER_SECURITY -- an answer you cannot re-type at recovery time is useless, so
    // the two patterns have to admit the same shapes.
    SECURITY_QUESTION("^\\s*pick\\s+question\\s+-q\\s+(?<questionNumber>\\S+)"
            + "\\s+-a\\s+(?<answer>\"[^\"]*\"|[^\"\\s]+)"
            + "\\s+-c\\s+(?<answerConfirm>\"[^\"]*\"|[^\"\\s]+)\\s*$");


    private final String pattern;

    SignUpMenuRegex(String pattern) {
        this.pattern = pattern;
    }
    @Override
    public String getPattern() {
        return pattern;
    }
}
