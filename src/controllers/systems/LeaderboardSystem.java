package controllers.systems;

import models.leaderboard.LbColumn;
import models.leaderboard.LeaderboardEntry;
import models.user.User;
import utils.storage.DatabaseManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// Builds and sorts the whole-game leaderboard from the registered-user roster. Stateless singleton:
// every call snapshots the current users into plain LeaderboardEntry rows and sorts a fresh copy, so
// the board always reflects the latest saved progress and no ordering leaks between requests.
public class LeaderboardSystem {
    private static LeaderboardSystem instance;

    private LeaderboardSystem() {}

    public static synchronized LeaderboardSystem getInstance() {
        if (instance == null) {
            instance = new LeaderboardSystem();
        }
        return instance;
    }

    // One row per registered player. Fetching is a single O(n) pass over the user map (no file I/O:
    // the database is already loaded in memory), and each row is a cheap scalar snapshot of the
    // player's Profile.
    public List<LeaderboardEntry> buildEntries(DatabaseManager databaseManager) {
        List<LeaderboardEntry> entries = new ArrayList<>();
        if (databaseManager == null) {
            return entries;
        }
        for (User user : databaseManager.getAllUsers()) {
            if (user != null && user.getUsername() != null) {
                entries.add(LeaderboardEntry.from(user));
            }
        }
        return entries;
    }

    // The leaderboard ordered by one column. isAscending flips the whole ordering (the "click a column
    // to sort ascending or descending" behaviour): descending simply reverses the column's ascending
    // comparator, keeping the username tie-break sensible in both directions.
    public List<LeaderboardEntry> sortBy(LbColumn column, boolean isAscending, DatabaseManager databaseManager) {
        List<LeaderboardEntry> entries = buildEntries(databaseManager);
        if (column == null) {
            return entries;
        }
        Comparator<LeaderboardEntry> comparator = isAscending
                ? column.ascendingComparator()
                : column.ascendingComparator().reversed();
        entries.sort(comparator);
        return entries;
    }
}
