package utils.regex;

public enum AllMenuRegex implements Regex{
    EXIT_MENU("^\\s*menu\\s+exit\\s*$");

    private final String pattern;

    AllMenuRegex(String pattern) {
        this.pattern = pattern;
    }

    @Override
    public String getPattern() {
        return this.pattern;
    }
}
