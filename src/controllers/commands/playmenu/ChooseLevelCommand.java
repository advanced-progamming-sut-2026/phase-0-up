package controllers.commands.playmenu;

import controllers.commands.Command;
import controllers.engine.GameEngine;
import controllers.engine.MenuType;
import models.game.GameSession;
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
        if(level < 1 || level > 4){
            renderer.chooseLevelRenderer(new Result(false , "level number is wrong!")); return;
        }
        if(!profile.getCurrentChapter().getLevels()[level - 1].isUnlocked()){
            renderer.chooseLevelRenderer(new Result(false , "this level is unavailable!")); return;
        }
        GameSession gameSession = new GameSession(profile , profile.getCurrentChapter().getLevels()[level - 1]);
        appSession.setCurrentGameSession(gameSession);
        appSession.setCurrentMenu(MenuType.PLANTS_MENU);
        renderer.chooseLevelRenderer(new Result(true , "you are now in Plants Menu!\nchoose your plants:"));
    }
}
