package utils.regex;

public enum GreenHouseMenuRegex implements Regex {
    ENTER_SHOP("^\\s*enter\\s+shop\\s*$"),
    PLANT("^\\s*plant\\s+pot\\s+at\\s+\\(\\s*(?<x>\\d+)\\s*,\\s*(?<y>\\d+)\\s*\\)\\s*$"),
    COLLECT("^\\s*collect\\s+\\(\\s*(?<x>\\d+)\\s*,\\s*(?<y>\\d+)\\s*\\)\\s*$"),
    SHOW_STATUS("^\\s*show\\s+greenhouse\\s*$"),
    GROW("^\\s*grow\\s+\\(\\s*(?<x>\\d+)\\s*,\\s*(?<y>\\d+)\\s*\\)\\s*$");

    private final String pattern;

    GreenHouseMenuRegex(String pattern) {
        this.pattern = pattern;
    }

    @Override
    public String getPattern() {
        return this.pattern;
    }
}
