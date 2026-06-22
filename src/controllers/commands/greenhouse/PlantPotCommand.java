package controllers.commands.greenhouse;

import controllers.commands.Command;
import models.greenhouse.GreenHouse;
import models.greenhouse.GreenHousePlant;
import models.greenhouse.Pot;
import models.user.AppSession;
import models.user.Profile;
import utils.Result;
import views.renderers.MenuRenderer.GreenhouseRenderer;

import java.util.Random;

public class PlantPotCommand implements Command {
    private GreenHouse greenHouse;
    private int potX;
    private int potY;
    private GreenhouseRenderer greenhouseRenderer;
    private AppSession appSession;

    public PlantPotCommand(int potX, int potY,
                           GreenhouseRenderer greenhouseRenderer, AppSession appSession) {
        this.greenHouse = appSession.getCurrentUser().getProfile().getMyGreenHouse();
        this.potX = potX;
        this.potY = potY;
        this.greenhouseRenderer = greenhouseRenderer;
        this.appSession = appSession;
    }

    @Override
    public void execute() {
        if (!greenHouse.isValidCoordinate(potX, potY)){
            greenhouseRenderer.plantPot(new Result(false, "Invalid coordinate"));
            return;
        }
        Pot pot = greenHouse.getPot(potX - 1, potY - 1);

        if (!pot.isEmpty()) {
            greenhouseRenderer.plantPot(new Result(false, "This pot is Unavailable!"));
            return;
        }

        Profile profile = appSession.getCurrentUser().getProfile();
        GreenHousePlant plantSeed;
        Random random = new Random();
        boolean isMarigold = random.nextBoolean();
        if (isMarigold){
            plantSeed = new GreenHousePlant("Marigold", true);
        } else {
            int randomIndex = random.nextInt(profile.getUnlockedPlants().size());
            String plantName = profile.getUnlockedPlants().get(randomIndex);
            plantSeed = new GreenHousePlant(plantName, false);
        }

        greenHouse.plantPot(potX - 1, potY - 1, plantSeed);
        greenhouseRenderer.plantPot(new Result(true, String.format("%s is planted at (%d, %d)", plantSeed.getName(), potX, potY)));
    }
}
