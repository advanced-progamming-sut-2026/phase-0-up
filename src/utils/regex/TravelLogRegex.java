package utils.regex;

// Commands available inside the Travel Log menu.
public enum TravelLogRegex implements Regex {
    // Switch to a travel-log page: "travel log page <page_name>" (main, daily, epic, minigames, all).
    PAGE("^\\s*travel\\s+log\\s+page\\s+(?<page>[\\w-]+)\\s*$");

    private final String pattern;

    TravelLogRegex(String pattern) {
        this.pattern = pattern;
    }

    @Override
    public String getPattern() {
        return pattern;
    }
}
