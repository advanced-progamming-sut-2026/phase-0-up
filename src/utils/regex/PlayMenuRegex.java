package utils.regex;

public enum PlayMenuRegex implements Regex{
    ENTER_CHAPTER("^\\s*menu\\s+enter\\s+chapter\\s+-c\\s+(?<chapter>\\S+)\\s*$"),
    ENTER_GREENHOUSE("^\\s*menu\\s+greenhouse\\s*$"),
    ENTER_TRAVEL_LOG("^\\s*menu\\s+travel-log\\s*$"),
    ENTER_LEADERBOARD("^\\s*menu\\s+leaderboard\\s*$"),
    SHOW_COINS("^\\s*menu\\s+coin-wallet\\s*$"),
    SHOW_GEMS("^\\s*menu\\s+gem-wallet\\s*$"),
    CHEAT_CODE("^\\s*menu\\s+cheat\\s+add\\s+(?<n>\\d+)\\s+(?<currency>coin|diamond)\\s*$");

    private final String pattern;

    PlayMenuRegex(String pattern) {
        this.pattern = pattern;
    }
    @Override
    public String getPattern() {
        return pattern;
    }
}
