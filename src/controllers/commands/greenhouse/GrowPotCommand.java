package controllers.commands.greenhouse;

import controllers.commands.Command;
import models.greenhouse.GreenHouse;
import models.greenhouse.Pot;
import models.greenhouse.PotState;
import models.user.AppSession;
import models.user.Profile;
import utils.Result;
import utils.storage.DatabaseManager;
import views.renderers.MenuRenderer.GreenhouseRenderer;

public class GrowPotCommand implements Command {
    private GreenhouseRenderer greenhouseRenderer;
    private AppSession appSession;
    private GreenHouse greenHouse;
    private int potX;
    private int potY;

    public GrowPotCommand(GreenhouseRenderer greenhouseRenderer, AppSession appSession, int potX, int potY) {
        this.greenhouseRenderer = greenhouseRenderer;
        this.appSession = appSession;
        this.greenHouse = appSession.getCurrentUser().getProfile().getMyGreenHouse();
        this.potX = potX;
        this.potY = potY;
    }

    @Override
    public void execute() {
        // The player types 1-based coordinates; GreenHouse is 0-based throughout. This is the only
        // place the two meet.
        int x = potX - 1;
        int y = potY - 1;
        if (!greenHouse.isValidCoordinate(x, y)){
            greenhouseRenderer.grow(new Result(false, "Invalid coordinate"));
            return;
        }

        Pot pot = greenHouse.getPot(x, y);
        pot.updateState();

        if (pot.isReady()){
            greenhouseRenderer.grow(new Result(true, "Plant is ready to collect"));
            return;
        }

        if (pot.getState() == PotState.EMPTY || pot.getState() == PotState.LOCKED){
            greenhouseRenderer.grow(new Result(false, "There is no plant on this pot!"));
            return;
        }

        int cost = greenHouse.getGrowthCostInGems(x, y);
        Profile profile = appSession.getCurrentUser().getProfile();

        if (profile.getGems() < cost){
            greenhouseRenderer.grow(new Result(false, "not enough Gems"));
            return;
        }

        profile.spendGems(cost);
        greenHouse.growPlantWithGems(x, y);
        // Saved AFTER the growth is applied, not between the payment and it.
        //
        // The old order wrote the gems leaving the wallet and then finished the plant in memory only, so
        // a player who quit before the next save had paid and still had a growing pot. Caught by reading
        // users_database.json after a speed-up and finding GROWING with the gems already gone.
        DatabaseManager.getInstance().saveAll();
        greenhouseRenderer.grow(new Result(true, "Plant is ready to collect"));
    }
}
