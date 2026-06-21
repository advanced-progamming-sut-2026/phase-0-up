package utils.regex;

public enum LoginMenuRegex implements Regex {
    LOGIN("^\\s*login\\s+-u\\s+(?<username>\\S+)\\s+-p\\s+(?<password>\\S+)(?:\\s+(?<stayLoggedIn>-stay-logged-in))?\\s*$");



    private final String pattern;

    LoginMenuRegex(String pattern) {
        this.pattern = pattern;
    }

    @Override
    public String getPattern() {
        return this.pattern;
    }
}
