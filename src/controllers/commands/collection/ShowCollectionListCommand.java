package controllers.commands.collection;

import controllers.commands.Command;
import models.user.User;
import utils.registry.PlantRegistry;
import utils.registry.ZombieRegistry;


public class ShowCollectionListCommand implements Command {
    private  User currentUser;
    private ShowListType type;
    private ZombieRegistry zombieRegistry;
    private PlantRegistry plantRegistry;

    @Override
    public void execute() {}
}
