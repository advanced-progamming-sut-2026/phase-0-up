package controllers.commands.collection;

import controllers.commands.Command;
import models.user.User;
import utils.registry.PlantRegistry;
import utils.registry.ZombieRegistry;

public class ShowEntityDetailsCommand implements Command {
    private User currentUser;
    private ZombieRegistry zombieRegistry;
    private PlantRegistry plantRegistry;
    private String entityName;

    @Override
    public void execute() {}
}
