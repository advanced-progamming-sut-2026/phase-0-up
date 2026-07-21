package utils.regex;

// Commands available inside the leaderboard menu. SORT is the CLI equivalent of "clicking a column":
// the player names a column and a direction, e.g.
//   leaderboard sort -c score -o desc
//   leaderboard sort -c stage -o asc
// SHOW re-renders the board with its default ordering.
public enum LeaderboardMenuRegex implements Regex {
    SORT("^\\s*leaderboard\\s+sort\\s+-c\\s+(?<column>[\\w-]+)\\s+-o\\s+(?<order>asc|ascending|desc|descending)\\s*$"),
    SHOW("^\\s*leaderboard\\s+show\\s*$");

    private final String pattern;

    LeaderboardMenuRegex(String pattern) {
        this.pattern = pattern;
    }

    @Override
    public String getPattern() {
        return pattern;
    }
}
