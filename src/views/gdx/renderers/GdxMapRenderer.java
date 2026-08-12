package views.gdx.renderers;

import models.game.GameSession;
import views.gdx.core.ToastSink;
import views.renderers.MapRenderer;

// The board, on a build that is already drawing the board.
//
// "show map" exists because a terminal cannot show you the lawn until you ask. Here the lawn is on the
// screen sixty times a second, so re-rendering it on demand would be meaningless -- the honest
// response is to acknowledge the command and report the header numbers that are NOT otherwise on
// screen yet. The HUD covers sun and wave; this fills the gap until the rest of the status line does.
public final class GdxMapRenderer implements MapRenderer {

    private final ToastSink toasts;

    public GdxMapRenderer(ToastSink toasts) {
        this.toasts = toasts;
    }

    @Override
    public void renderAllTheMap(GameSession activeSession) {
        // Nothing to do: GameRenderer already draws every cell, plant, zombie and projectile.
    }

    @Override
    public void renderGameSession(GameSession activeSession) {
        if (activeSession == null) {
            return;
        }
        toasts.info("Wave " + activeSession.getCurrentWave()
                + "  |  sun " + activeSession.getSunAmount()
                + "  |  plant food " + activeSession.getPlantFoodCount());
    }
}
