package models.leaderboard;

import java.util.Comparator;

// A sortable leaderboard column. Each column knows its table header, the CLI tokens a player can name
// it by (so "leaderboard sort -c score" and "-c mu-points" both resolve to MYOPOINT), and how to
// compare two rows on it. Sorting logic lives here so the system and renderer never duplicate it.
public enum LbColumn {
    LEVEL("Stage", "level", "levels", "stage", "stages", "chapter"),
    MINIGAMES("Mini-games", "minigames", "minigame", "mini-games", "mini-game", "mg"),
    DAILY_QUESTS("Daily Quests", "daily", "daily-quests", "dailyquests", "dq"),
    NONDAILY_QUESTS("Non-Daily Quests", "nondaily", "non-daily", "nondaily-quests", "non-daily-quests", "ndq"),
    MYOPOINT("Mu-Points", "score", "scores", "points", "mu", "mu-points", "mupoints", "myopoint", "myo");

    private final String displayName;
    private final String[] tokens;

    LbColumn(String displayName, String... tokens) {
        this.displayName = displayName;
        this.tokens = tokens;
    }

    public String getDisplayName() {
        return displayName;
    }

    // Resolve a user-typed column token (case-insensitive) to a column, accepting either the enum
    // constant name or any of the friendly aliases above. Returns null for an unknown token.
    public static LbColumn fromToken(String token) {
        if (token == null) {
            return null;
        }
        String t = token.toLowerCase().trim();
        for (LbColumn col : values()) {
            if (col.name().equalsIgnoreCase(t)) {
                return col;
            }
            for (String alias : col.tokens) {
                if (alias.equals(t)) {
                    return col;
                }
            }
        }
        return null;
    }

    // Ascending comparator for this column. LEVEL orders by chapter then level so that, e.g., stage
    // 2-1 ranks above 1-4. Ties on the sort key fall back to username so the ordering is stable and
    // deterministic across calls.
    public Comparator<LeaderboardEntry> ascendingComparator() {
        Comparator<LeaderboardEntry> byColumn = switch (this) {
            case LEVEL -> Comparator.comparingInt(LeaderboardEntry::getLastChapter)
                    .thenComparingInt(LeaderboardEntry::getLastLevel);
            case MINIGAMES -> Comparator.comparingInt(LeaderboardEntry::getMinigamesCompleted);
            case DAILY_QUESTS -> Comparator.comparingInt(LeaderboardEntry::getDailyQuests);
            case NONDAILY_QUESTS -> Comparator.comparingInt(LeaderboardEntry::getNonDailyQuests);
            case MYOPOINT -> Comparator.comparingInt(LeaderboardEntry::getBestMyoPoint);
        };
        return byColumn.thenComparing(LeaderboardEntry::getUsername, String.CASE_INSENSITIVE_ORDER);
    }
}
