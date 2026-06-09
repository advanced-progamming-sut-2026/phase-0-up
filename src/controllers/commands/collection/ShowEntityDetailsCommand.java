package controllers.commands.collection;

import controllers.commands.Command;
import models.user.User;

public class ShowEntityDetailsCommand implements Command {
    private User currentUser;
    private ZombieRegistry zombieRegistry;
    private PlantRegistry plantRegistry;
    private String entityName;

    @Override
    public void execute() {}
}
