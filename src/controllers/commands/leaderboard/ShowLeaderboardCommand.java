package controllers.commands.leaderboard;

import controllers.commands.Command;
import controllers.systems.LeaderboardSystem;
import models.leaderboard.LbColumn;
import models.leaderboard.LeaderboardEntry;
import utils.storage.DatabaseManager;
import views.renderers.MenuRenderer.LeaderboardRenderer;

import java.util.List;

// Fetches the whole-game leaderboard sorted on one column in one direction and hands it to the
// renderer. Used both for the initial view when entering the menu and for every "leaderboard sort"
// command, so display and sorting share a single code path.
public class ShowLeaderboardCommand implements Command {
    private final LeaderboardSystem leaderboardSystem;
    private final DatabaseManager databaseManager;
    private final LbColumn column;
    private final boolean ascending;
    private final LeaderboardRenderer renderer;

    public ShowLeaderboardCommand(LeaderboardSystem leaderboardSystem, DatabaseManager databaseManager,
                                  LbColumn column, boolean ascending, LeaderboardRenderer renderer) {
        this.leaderboardSystem = leaderboardSystem;
        this.databaseManager = databaseManager;
        this.column = column;
        this.ascending = ascending;
        this.renderer = renderer;
    }

    @Override
    public void execute() {
        List<LeaderboardEntry> entries = leaderboardSystem.sortBy(column, ascending, databaseManager);
        renderer.renderLeaderboard(entries, column, ascending);
    }
}
