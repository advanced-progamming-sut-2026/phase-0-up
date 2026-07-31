package utils.regex;

public enum PlayMenuRegex implements Regex{
    ENTER_CHAPTER("^\\s*menu\\s+enter\\s+chapter\\s+-c\\s+(?<chapter>\\S+)\\s*$"),
    ENTER_GREENHOUSE("^\\s*menu\\s+greenhouse\\s*$"),
    ENTER_TRAVEL_LOG("^\\s*menu\\s+travel-log\\s*$"),
    ENTER_LEADERBOARD("^\\s*menu\\s+leaderboard\\s*$"),
    // The scoring game is reachable straight from the main menu as well as the play menu, per the spec.
    PLAY_SCORING_GAME("^\\s*menu\\s+scoring-game\\s*$"),
    SHOW_COINS("^\\s*menu\\s+coin-wallet\\s*$"),
    SHOW_GEMS("^\\s*menu\\s+gem-wallet\\s*$"),
    // "gem" is the game's word for this currency and the only one it ever prints. "diamond" stays
    // accepted purely as an input spelling, because that is how the spec writes the command
    // (project.md line 462: "menu cheat add <n> <coin/diamond>") -- typing it does the same thing and
    // still reports back in gems, so nobody following the spec hits a dead end.
    CHEAT_CODE("^\\s*menu\\s+cheat\\s+add\\s+(?<n>\\d+)\\s+(?<currency>coin|gem|diamond)\\s*$"),
    CHOOSE_LEVEL("^\\s*level\\s+-l\\s+(?<level>\\d+)\\s*$");

    private final String pattern;

    PlayMenuRegex(String pattern) {
        this.pattern = pattern;
    }
    @Override
    public String getPattern() {
        return pattern;
    }
}
