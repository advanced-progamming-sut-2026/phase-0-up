package controllers.commands.ingame;

import controllers.commands.Command;
import controllers.systems.game.QuestSystem;
import controllers.systems.game.SunSystem;
import models.entities.collectibles.SunType;
import models.game.GameSession;
import utils.Constants;
import utils.Result;
import views.renderers.InGameRenderer;

public class CollectSunCommand implements Command {
    private GameSession gameSession;
    private SunSystem sunSystem;
    private InGameRenderer renderer;
    private final QuestSystem questSystem;
    private int x;
    private int y;

    public CollectSunCommand(GameSession gameSession, SunSystem sunSystem, InGameRenderer renderer,QuestSystem questSystem, int x, int y) {
        this.gameSession = gameSession;
        this.sunSystem = sunSystem;
        this.renderer = renderer;
        this.x = x;
        this.y = y;
        this.questSystem = questSystem;
    }


    @Override
    public void execute() {
        if(!isValidCoordinate(x,y)){
            renderer.render(new Result(false , "Invalid coordinates (" + x + ", " + y + ")."));
            return;
        }

        int SunBefore = gameSession.getSunAmount();
        boolean collected = sunSystem.collectSun(gameSession , x , y);
        if(!collected){
            renderer.render(new Result(false , "No sun to collect at (" + x + ", " + y + ")."));
            return;
        }

        int gained = gameSession.getSunAmount() - SunBefore;
        if(questSystem != null){
            questSystem.recordSunCollected(gained);
        }
        renderer.render(new Result(true , "Collected " + gained + " sun."));
    }

    private boolean isValidCoordinate(int x, int y) {
        return x >= 0 && x < Constants.BOARD_COLS && y >= 0 && y < Constants.BOARD_ROWS;
    }
}
