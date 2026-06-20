package utils.regex;

public enum ProfileMenuRegex implements Regex{
    CHANGE_USERNAME("^\\s*menu\\s+profile\\s+change-username\\s+-u\\s+(?<username>\\S+)\\s*$"),
    CHANGE_NICKNAME("^\\s*menu\\s+profile\\s+change-nickname\\s+-u\\s+(?<nickname>\\S+)\\s*$"),
    CHANGE_EMAIL("^\\s*menu\\s+profile\\s+change-email\\s+-e\\s+(?<email>\\S+)\\s*$"),
    CHANGE_PASS("^\\s*menu\\s+profile\\s+change-password\\s+-p\\s+(?<newP>\\S+)\\s+-o\\s+(?<oldP>\\S+)\\s*$"),
    SHOW_INFO("^\\s*menu\\s+profile\\s+show-info\\s*");

    private final String pattern;

    ProfileMenuRegex(String pattern) {
        this.pattern = pattern;
    }
    @Override
    public String getPattern() {
        return pattern;
    }
}
