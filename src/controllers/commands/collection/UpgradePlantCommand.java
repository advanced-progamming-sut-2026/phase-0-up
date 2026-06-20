package controllers.commands.collection;

import controllers.commands.Command;
import controllers.systems.CollectionSystem;
import models.user.Profile;
import models.user.User;

public class UpgradePlantCommand implements Command {
    private String plantName;
    private User currentUser;

    public UpgradePlantCommand(String plantName, User currentUser) {
        this.plantName = plantName;
        this.currentUser = currentUser;
    }

    @Override
    public void execute() {
        Profile profile = currentUser.getProfile();
        CollectionSystem.getInstance().upgradePlant(profile, plantName);
    }
}
