package controllers.systems;

import models.leaderboard.LbColumn;
import models.leaderboard.LeaderboardEntry;
import utils.storage.DatabaseManager;

import java.util.List;

public class LeaderboardSystem {
    private static LeaderboardSystem instance;

    private LeaderboardSystem() {}

    public static synchronized LeaderboardSystem getInstance() {
        if (instance == null) {
            instance = new LeaderboardSystem();
        }
        return instance;
    }

    public List<LeaderboardEntry> sortBy(LbColumn column, boolean isAscending, DatabaseManager databaseManager){
        return null;
    }

    private int compareEntries(LeaderboardEntry e1, LeaderboardEntry e2, LbColumn col){return 0;}
}
