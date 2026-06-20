package utils.regex;

public enum AllMenuRegex implements Regex{
    EXIT_MENU("^\\s*menu\\s+exit\\s*$"),
    ENTER_MENU("^\\s*menu\\s+enter\\s+(?<menuName>\\S+)\\s*$"),
    SHOW_CURRENT("^\\s*menu\\s+show\\s+current\\s*$");


    private final String pattern;

    AllMenuRegex(String pattern) {
        this.pattern = pattern;
    }

    @Override
    public String getPattern() {
        return this.pattern;
    }
}
