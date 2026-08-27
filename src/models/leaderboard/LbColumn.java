package models.leaderboard;

import java.util.Comparator;

// A sortable leaderboard column. Each column knows its table header, the CLI tokens a player can name
// it by (so "leaderboard sort -c score" and "-c meow-points" both resolve to MEOW_POINT), and how to
// compare two rows on it. Sorting logic lives here so the system and renderer never duplicate it.
public enum LbColumn {
    LEVEL("Stage", "level", "levels", "stage", "stages", "chapter"),
    MINIGAMES("Mini-games", "minigames", "minigame", "mini-games", "mini-game", "mg"),
    DAILY_QUESTS("Daily Quests", "daily", "daily-quests", "dailyquests", "dq"),
    NONDAILY_QUESTS("Non-Daily Quests", "nondaily", "non-daily", "nondaily-quests", "non-daily-quests", "ndq"),
    MEOW_POINT("Meow Points", "score", "scores", "points",
            "meow", "meow-points", "meowpoints",
            "mu", "mu-points", "mupoints", "myopoint", "myo");   // older spellings still accepted

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
            // Null is "never played", and it sorts BELOW every real score -- including below zero,
            // because not having played is not an achievement. nullsFirst on the ASCENDING comparator
            // is what puts them at the bottom of the default (descending) board, and it also keeps
            // the reversal in sortBy meaningful in both directions.
            //
            // comparingInt here would unbox and throw the moment any player has never played, which
            // on a fresh server is every single row.
            case MEOW_POINT -> Comparator.comparing(LeaderboardEntry::getBestMeowPoint,
                    Comparator.nullsFirst(Comparator.naturalOrder()));
        };
        return byColumn.thenComparing(LeaderboardEntry::getUsername, String.CASE_INSENSITIVE_ORDER);
    }

    // The ordering in whichever direction was asked for. Descending simply reverses the ascending
    // comparator, which keeps the username tie-break sensible both ways round.
    //
    // Here rather than at each caller because there are now three of them -- LeaderboardSystem, the
    // local storage backend, and the server's handler -- and "how a leaderboard is ordered" is exactly
    // the two-line rule that drifts when it is written three times.
    public Comparator<LeaderboardEntry> comparator(boolean ascending) {
        return ascending ? ascendingComparator() : ascendingComparator().reversed();
    }
}
