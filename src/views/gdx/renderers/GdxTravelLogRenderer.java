package views.gdx.renderers;

import models.quests.Quest;
import models.user.Profile;
import views.gdx.core.ToastSink;
import views.renderers.MenuRenderer.TravelLogRenderer;

import java.util.List;

// The Travel Log. TravelLogScreen (T6.3) draws the quest cards and their progress bars; a mini-game
// launch is an event worth announcing over the top of whatever screen follows it.
public final class GdxTravelLogRenderer implements TravelLogRenderer {

    private final ToastSink toasts;

    public GdxTravelLogRenderer(ToastSink toasts) {
        this.toasts = toasts;
    }

    @Override
    public void showQuestPage(String pageTitle, List<Quest> quests, Profile profile) {
        // The screen is the page.
    }

    @Override
    public void showMinigamesPage(List<String> minigames) {
        // Likewise -- the mini-game tiles are the page.
    }

    @Override
    public void unknownPage(String pageName) {
        toasts.error("No travel-log page named \"" + pageName + "\".");
    }

    @Override
    public void launchingMinigame(String name, int difficulty) {
        toasts.success("Launching " + name + " (difficulty " + difficulty
                + "). Don't let a zombie reach your house!");
    }

    @Override
    public void minigameUnavailable(String name) {
        toasts.error("No mini-game called \"" + name + "\".");
    }
}
