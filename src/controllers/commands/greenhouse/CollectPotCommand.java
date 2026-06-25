package controllers.commands.greenhouse;

import controllers.commands.Command;
import models.game.SeedPacket;
import models.greenhouse.GreenHouse;
import models.greenhouse.GreenHousePlant;
import models.greenhouse.Pot;
import models.greenhouse.PotState;
import models.user.AppSession;
import models.user.Profile;
import utils.Result;
import views.renderers.MenuRenderer.GreenhouseRenderer;

public class CollectPotCommand implements Command {
    private AppSession appSession;
    private GreenhouseRenderer  greenhouseRenderer;
    private int potX;
    private int potY;

    public CollectPotCommand(AppSession appSession, GreenhouseRenderer greenhouseRenderer, int potX, int potY) {
        this.appSession = appSession;
        this.greenhouseRenderer = greenhouseRenderer;
        this.potX = potX;
        this.potY = potY;
    }

    @Override
    public void execute() {
        GreenHouse greenHouse = appSession.getCurrentUser().getProfile().getMyGreenHouse();
        if (!greenHouse.isValidCoordinate(potX, potY)){
            greenhouseRenderer.plantPot(new Result(false, "Invalid coordinate"));
            return;
        }

        Pot pot = greenHouse.getPot(potX - 1, potY - 1);

        pot.updateState();

        if (pot.getState() == PotState.EMPTY || pot.getState() == PotState.LOCKED){
            greenhouseRenderer.invalidPotState(pot.getState());
            return;
        }

        if (!pot.isReady()){
            greenhouseRenderer.potNotReadyYet(pot);
            return;
        }

        GreenHousePlant harvested = greenHouse.collect(potX - 1, potY - 1);
        Profile profile = appSession.getCurrentUser().getProfile();

        if (harvested.isMarigold()){
            profile.addCoins(500);
            greenhouseRenderer.collect(new Result(true, "Collected a Marigold! +500 coins."));
        }
        else {
            String seedName = harvested.getName().toLowerCase().trim();

            // بررسی وضعیت بوست با استفاده از ساختار جدید و بدون نیاز به حلقه‌های پیچیده
            if (profile.isSeedBoosted(seedName)){
                greenhouseRenderer.collect(new Result(false, harvested.getName() + " seed Already Boosted"));
            }
            else {
                profile.setSeedBoosted(seedName, true);
                greenhouseRenderer.collect(new Result(true, harvested.getName() + " seed got Boosted"));
            }
        }
    }
}
