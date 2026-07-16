package controllers.commands.playmenu;

import controllers.commands.Command;
import controllers.engine.GameEngine;
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
        renderer.chooseLevelRenderer(new Result(true , "you are now in Plants Menu!\nchoose your plants:"));
    }
}
