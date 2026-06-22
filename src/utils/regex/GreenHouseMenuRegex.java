package utils.regex;

public enum GreenHouseMenuRegex implements Regex {
    ENTER_SHOP("^\\s*enter\\s+shop\\s*$"),
    PLANT("^\\s*plant\\s+pot\\s+at\\s+\\((?<x>\\d+),\\s+(?<y>\\d+)\\)\\s*$"),
    COLLECT("^\\s*collect\\s+\\((?<x>\\d+),\\s*(?<y>\\d+)\\)\\s*$"),
    SHOW_STATUS("^\\s*show\\s+greenhouse\\s*$"),
    GROW("^\\s*grow\\s+\\((?<x>\\d+),\\s*(?<y>\\d+)\\)\\s*$");

    private final String pattern;

    GreenHouseMenuRegex(String pattern) {
        this.pattern = pattern;
    }

    @Override
    public String getPattern() {
        return this.pattern;
    }
}
