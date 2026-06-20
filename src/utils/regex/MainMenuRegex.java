package utils.regex;

public enum MainMenuRegex implements Regex{
    LOG_OUT("^\\s*menu\\s+logout\\s*$");

    private final String pattern;

    MainMenuRegex(String pattern) {
        this.pattern = pattern;
    }

    @Override
    public String getPattern() {
        return pattern;
    }
}
