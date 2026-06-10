package controllers.commands.collection;

import controllers.commands.Command;
import models.user.User;
import utils.registry.PlantRegistry;

public class UnlockPlantCommand implements Command {
    private String plantName;
    private User currentUser;
    private PlantRegistry plantRegistry;

    @Override
    public void execute() {}
}
