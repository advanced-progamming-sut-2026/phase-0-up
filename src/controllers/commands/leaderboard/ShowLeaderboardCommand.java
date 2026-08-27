package controllers.commands.leaderboard;

import controllers.commands.Command;
import models.leaderboard.LbColumn;
import models.leaderboard.LeaderboardEntry;
import utils.storage.DatabaseManager;
import views.renderers.MenuRenderer.LeaderboardRenderer;

import java.util.List;

// Fetches the whole-game leaderboard sorted on one column in one direction and hands it to the
// renderer. Used both for the initial view when entering the menu and for every "leaderboard sort"
// command, so display and sorting share a single code path.
public class ShowLeaderboardCommand implements Command {
    private final DatabaseManager databaseManager;
    private final LbColumn column;
    private final boolean ascending;
    private final LeaderboardRenderer renderer;

    // No LeaderboardSystem parameter any more: the rows come from the storage backend, which is what
    // lets the same command serve a local roster and a server-held one. The sorter is still the single
    // one -- LbColumn's comparator -- it is simply applied wherever the rows actually live.
    public ShowLeaderboardCommand(DatabaseManager databaseManager,
                                  LbColumn column, boolean ascending, LeaderboardRenderer renderer) {
        this.databaseManager = databaseManager;
        this.column = column;
        this.ascending = ascending;
        this.renderer = renderer;
    }

    @Override
    public void execute() {
        // Asked of the storage layer rather than assembled from the user roster here.
        //
        // The terminal build's local backend answers exactly as before -- same rows, same ordering, via
        // the same LbColumn comparator. The graphical build's remote backend asks the server, which is
        // what the spec means by the leaderboard being retrieved from the users' data stored there.
        // This command cannot tell the difference, which is the point: one code path, both builds.
        List<LeaderboardEntry> entries = databaseManager.leaderboard(column, ascending);
        renderer.renderLeaderboard(entries, column, ascending);
    }
}
