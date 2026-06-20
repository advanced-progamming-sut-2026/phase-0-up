package controllers.commands.collection;

import controllers.commands.Command;
import controllers.systems.CollectionSystem;
import models.user.Profile;
import models.user.User;
import utils.registry.PlantRegistry;

public class UnlockPlantCommand implements Command {
    private String plantName;
    private User currentUser;
    private PlantRegistry plantRegistry;

    public UnlockPlantCommand(String plantName, User currentUser) {
        this.plantName = plantName;
        this.currentUser = currentUser;
        this.plantRegistry = PlantRegistry.getInstance();
    }

    @Override
    public void execute() {
        Profile profile = currentUser.getProfile();
        CollectionSystem.getInstance().purchasePlant(profile, plantName);
    }
}
