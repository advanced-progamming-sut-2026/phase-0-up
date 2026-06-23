package utils.regex;

public enum NewsMenuRegex implements Regex{
    SHOW_ALL("^\\s*menu\\s+news\\s+show\\s+-\\s+unread\\s*$"),
    SHOW_UNREAD("^\\s*menu\\s+news\\s+show\\s+all\\s*$");

    private final String pattern;

    NewsMenuRegex(String pattern) {
        this.pattern = pattern;
    }

    @Override
    public String getPattern() {
        return pattern;
    }
}
