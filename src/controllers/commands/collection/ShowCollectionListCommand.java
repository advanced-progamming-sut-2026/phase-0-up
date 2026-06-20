package controllers.commands.collection;

import controllers.commands.Command;
import models.user.Profile;
import models.user.User;
import utils.registry.PlantRegistry;
import utils.registry.ZombieRegistry;
import views.renderers.MenuRenderer.CollectionMenuRenderer;


public class ShowCollectionListCommand implements Command {
    private  User currentUser;
    private ShowListType type;
    private ZombieRegistry zombieRegistry;
    private PlantRegistry plantRegistry;

    public ShowCollectionListCommand(ShowListType type, User currentUser) {
        this.type = type;
        this.currentUser = currentUser;
        this.plantRegistry = PlantRegistry.getInstance();
        this.zombieRegistry = ZombieRegistry.getInstance();
    }

    @Override
    public void execute() {
        Profile profile = currentUser.getProfile();
        CollectionMenuRenderer renderer = new CollectionMenuRenderer();
        switch (type){
            case PLANTS -> renderer.renderUnlockedPlants(profile);
            case ALL_PLANTS -> renderer.renderAllPlants(plantRegistry);
            case ZOMBIES -> renderer.renderSeenZombies(profile);
            case ALL_ZOMBIES -> renderer.renderAllZombies(zombieRegistry);
        }
    }
}
