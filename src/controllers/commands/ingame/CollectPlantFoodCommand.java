package controllers.commands.ingame;

import controllers.commands.Command;
import controllers.systems.game.PlantFoodSystem;
import models.game.GameSession;
import utils.Constants;
import utils.Result;
import views.renderers.InGameRenderer;

// "collect plant-food -l (x, y)" -- the pickup a glowing zombie left behind.
//
// Deliberately the same shape as CollectSunCommand, down to the coordinate check and the two failure
// messages, because from the player's side it is the same gesture: name a tile, take what is on it.
// The GUI synthesises this string when the pickup is clicked, so the click path and the typed path are
// the same code below the bridge.
public class CollectPlantFoodCommand implements Command {
    private final GameSession gameSession;
    private final PlantFoodSystem plantFoodSystem;
    private final InGameRenderer renderer;
    private final int x;
    private final int y;

    public CollectPlantFoodCommand(GameSession gameSession, PlantFoodSystem plantFoodSystem,
                                   InGameRenderer renderer, int x, int y) {
        this.gameSession = gameSession;
        this.plantFoodSystem = plantFoodSystem;
        this.renderer = renderer;
        this.x = x;
        this.y = y;
    }

    @Override
    public void execute() {
        if (!isValidCoordinate(x, y)) {
            renderer.render(new Result(false, "Invalid coordinates (" + x + ", " + y + ")."));
            return;
        }

        // Read before, so a pickup taken on a full stock can say so instead of claiming a gain that
        // never happened -- the player can hold three at most (Constants.MAX_PLANT_FOOD_CAPACITY).
        int before = gameSession.getPlantFoodCount();
        if (!plantFoodSystem.collectPlantFood(gameSession, x, y)) {
            renderer.render(new Result(false, "No plant food to collect at (" + x + ", " + y + ")."));
            return;
        }

        int now = gameSession.getPlantFoodCount();
        if (now == before) {
            renderer.render(new Result(true, "Your plant food pouch is already full at " + now
                    + " -- that one goes to waste!"));
            return;
        }
        renderer.render(new Result(true, "Plant food collected; you have " + now
                + " plant foods now."));
    }

    private boolean isValidCoordinate(int x, int y) {
        return x >= 0 && x < Constants.BOARD_COLS && y >= 0 && y < Constants.BOARD_ROWS;
    }
}
