package controllers.commands.collection;

import controllers.commands.Command;
import models.user.User;


public class ShowCollectionListCommand implements Command {
    private  User currentUser;
    private ShowListType type;
    private ZombieRegistry zombieRegistry;
    private PlantRegistry plantRegistry;

    @Override
    public void execute() {}
}
