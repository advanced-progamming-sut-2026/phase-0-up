package controllers.commands.playmenu;

import controllers.commands.Command;
import controllers.engine.MenuType;
import models.game.Chapter;
import models.game.GameSession;
import models.game.Level;
import models.user.AppSession;
import models.user.Profile;
import utils.Result;
import views.renderers.MenuRenderer.PlayMenuRenderer;

public class ChooseLevelCommand implements Command {
    private int level;
    private Profile profile;
    private PlayMenuRenderer renderer;
    private AppSession appSession;

    public ChooseLevelCommand(int level, Profile profile, PlayMenuRenderer renderer , AppSession appSession) {
        this.level = level;
        this.profile = profile;
        this.renderer = renderer;
        this.appSession = appSession;
    }

    @Override
    public void execute() {
        Chapter chapter = profile.getCurrentChapter();
        if(chapter == null || chapter.getLevels() == null){
            renderer.chooseLevelRenderer(new Result(false , "no chapter is loaded!")); return;
        }
        // Bound by the chapter's real size rather than a hard-coded 4, so a data change can't index
        // past the end of the array.
        if(level < 1 || level > chapter.getLevels().length){
            renderer.chooseLevelRenderer(new Result(false , "level number is wrong!")); return;
        }
        Level chosen = chapter.getLevels()[level - 1];
        if(chosen == null || !chosen.isUnlocked()){
            renderer.chooseLevelRenderer(new Result(false , "this level is unavailable!")); return;
        }
        GameSession gameSession = new GameSession(profile , chosen);
        appSession.setCurrentGameSession(gameSession);
        appSession.setCurrentMenu(MenuType.PLANTS_MENU);
        renderer.chooseLevelRenderer(new Result(true , enteringMessage(gameSession)));
    }

    // What to tell the player they have arrived at.
    //
    // Every adventure level goes to the plants menu, including the one that has nothing to pick: a boss
    // level hands its plants out on a conveyor, so its mode manages the inventory itself and addSeed
    // refuses every packet. "choose your plants:" over a menu where choosing does nothing is the message
    // that made that read as a broken screen rather than as a level with no loadout. The graphical build
    // skips the screen outright (SeedSelectionScreen.skipIfNoLoadout); the terminal has no screen to
    // skip, so it gets told to go straight in.
    private static String enteringMessage(GameSession gameSession) {
        // Front-end neutral on purpose. The graphical build skips the screen but still shows this line
        // as a toast on the way past, so a terminal instruction ("type start") would be a puzzle to a
        // player who never sees a prompt -- and the command is the same "start game" every other level
        // takes anyway.
        if (gameSession.getMode() != null && !gameSession.getMode().requiresSeedSelection(gameSession)) {
            return "No seed selection on this one -- your plants arrive on a conveyor.";
        }
        return "you are now in Plants Menu!\nchoose your plants:";
    }
}
