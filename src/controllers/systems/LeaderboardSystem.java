package controllers.systems;

import models.leaderboard.LbColumn;
import models.leaderboard.LeaderboardEntry;
import models.user.User;
import utils.storage.DatabaseManager;

import java.util.ArrayList;
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
        return buildEntries(databaseManager == null ? null : databaseManager.getAllUsers());
    }

    // The roster-taking form, which is the one that actually does the work.
    //
    // Added for Phase 3, where two callers hold a roster but no DatabaseManager to hand over:
    // LocalFileBackend owns its user map directly, and the server builds the board from its own. Both
    // route through here rather than each sorting for itself, so the ordering rules -- including the
    // username tie-break and where a never-played score ranks -- stay written exactly once.
    public List<LeaderboardEntry> buildEntries(java.util.Collection<User> users) {
        List<LeaderboardEntry> entries = new ArrayList<>();
        if (users == null) {
            return entries;
        }
        for (User user : users) {
            if (user != null && user.getUsername() != null) {
                entries.add(LeaderboardEntry.from(user));
            }
        }
        return entries;
    }

    // The leaderboard ordered by one column. isAscending flips the whole ordering (the "click a column
    // to sort ascending or descending" behaviour): descending simply reverses the column's ascending
    // comparator, keeping the username tie-break sensible in both directions.
    public List<LeaderboardEntry> sortBy(LbColumn column, boolean isAscending,
                                         DatabaseManager databaseManager) {
        return sort(column, isAscending, buildEntries(databaseManager));
    }

    public List<LeaderboardEntry> sortBy(LbColumn column, boolean isAscending,
                                         java.util.Collection<User> users) {
        return sort(column, isAscending, buildEntries(users));
    }

    private List<LeaderboardEntry> sort(LbColumn column, boolean isAscending,
                                        List<LeaderboardEntry> entries) {
        if (column == null) {
            return entries;
        }
        // The ordering itself is LbColumn's, so this and the storage backend cannot disagree about it.
        entries.sort(column.comparator(isAscending));
        return entries;
    }
}
