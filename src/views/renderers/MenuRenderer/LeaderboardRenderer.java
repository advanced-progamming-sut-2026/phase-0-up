package views.renderers.MenuRenderer;

import models.leaderboard.LbColumn;
import models.leaderboard.LeaderboardEntry;

import java.util.List;

// The leaderboard. `entries` arrives in its final order; sortedBy/ascending are only there so the view
// can show WHICH column the board is sorted on -- they are never used to re-sort.
public interface LeaderboardRenderer {
    void renderLeaderboard(List<LeaderboardEntry> entries, LbColumn sortedBy, boolean ascending);

    void unknownColumn(String token);

    void unknownOrder(String token);
}
