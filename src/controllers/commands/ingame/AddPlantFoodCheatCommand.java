package controllers.commands.ingame;

import controllers.commands.Command;
import models.game.GameSession;
import utils.Result;
import views.renderers.InGameRenderer;

// "cheat add-plant-food": grants one unit of Plant Food to the current game. Mirrors the other in-game
// cheats (add sun, remove cooldown); the count is capped by GameSession.increasePlantFoodCount at the
// game's maximum plant-food capacity.
public class AddPlantFoodCheatCommand implements Command {
    private final GameSession gameSession;
    private final InGameRenderer renderer;

    public AddPlantFoodCheatCommand(GameSession gameSession, InGameRenderer renderer) {
        this.gameSession = gameSession;
        this.renderer = renderer;
    }

    @Override
    public void execute() {
        gameSession.increasePlantFoodCount(1);
        renderer.render(new Result(true, "One fresh plant food, coming up! You now have "
                + gameSession.getPlantFoodCount() + "."));
    }
}
