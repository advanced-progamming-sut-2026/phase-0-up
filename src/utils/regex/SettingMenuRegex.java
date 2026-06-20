package utils.regex;

public enum SettingMenuRegex implements Regex{
    CHANGE_DL("^\\s*menu\\s+settings\\s+change-difficulty\\s+-l\\s+(?<dl>\\d+)\\s*$");

    private final String pattern;

    SettingMenuRegex(String pattern) {
        this.pattern = pattern;
    }
    @Override
    public String getPattern() {
        return pattern;
    }
}
