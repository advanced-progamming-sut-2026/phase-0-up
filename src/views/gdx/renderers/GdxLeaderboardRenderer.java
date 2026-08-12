package views.gdx.renderers;

import models.leaderboard.LbColumn;
import models.leaderboard.LeaderboardEntry;
import views.gdx.core.ToastSink;
import views.renderers.MenuRenderer.LeaderboardRenderer;

import java.util.List;

// The leaderboard. LeaderboardScreen (T5.2) draws the table with clickable column headers, which is
// also why the two "you named something that isn't a column" refusals stay useful only here: on a
// screen you cannot mistype a column, you click one.
public final class GdxLeaderboardRenderer implements LeaderboardRenderer {

    private final ToastSink toasts;

    public GdxLeaderboardRenderer(ToastSink toasts) {
        this.toasts = toasts;
    }

    @Override
    public void renderLeaderboard(List<LeaderboardEntry> entries, LbColumn sortedBy, boolean ascending) {
        if (entries == null || entries.isEmpty()) {
            toasts.info("No registered players to show yet.");
        }
        // Otherwise the screen draws them.
    }

    @Override
    public void unknownColumn(String token) {
        toasts.error("Unknown column '" + token + "'.");
    }

    @Override
    public void unknownOrder(String token) {
        toasts.error("Unknown order '" + token + "'. Use 'asc' or 'desc'.");
    }
}
