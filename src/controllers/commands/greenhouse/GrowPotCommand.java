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
        if (!greenHouse.isValidCoordinate(potX, potY)){
            greenhouseRenderer.grow(new Result(false, "Invalid coordinate"));
            return;
        }

        Pot pot = greenHouse.getPot(potX - 1, potY - 1);
        pot.updateState();

        if (pot.isReady()){
            greenhouseRenderer.grow(new Result(true, "Plant is ready to collect"));
            return;
        }

        if (pot.getState() == PotState.EMPTY || pot.getState() == PotState.LOCKED){
            greenhouseRenderer.grow(new Result(false, "There is no plant on this pot!"));
            return;
        }

        int cost = greenHouse.getGrowthCostInDiamonds(potX - 1, potY - 1);
        Profile profile = appSession.getCurrentUser().getProfile();

        if (profile.getGems() < cost){
            greenhouseRenderer.grow(new Result(false, "not enough Gems"));
            return;
        }

        profile.spendGems(cost);
        DatabaseManager.getInstance().saveAll();
        greenHouse.growPlantWithDiamonds(potX - 1, potY - 1);
        greenhouseRenderer.grow(new Result(true, "Plant is ready to collect"));
    }
}
