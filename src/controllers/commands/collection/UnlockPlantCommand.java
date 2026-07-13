package controllers.commands.collection;

import controllers.commands.Command;
import controllers.systems.CollectionSystem;
import models.user.Profile;
import models.user.User;
import utils.registry.PlantRegistry;
import utils.storage.DatabaseManager;
import views.renderers.MenuRenderer.CollectionMenuRenderer;

public class UnlockPlantCommand implements Command {
    private String plantName;
    private User currentUser;
    private PlantRegistry plantRegistry;
    private CollectionMenuRenderer renderer;

    public UnlockPlantCommand(String plantName, User currentUser, CollectionMenuRenderer renderer) {
        this.plantName = plantName;
        this.currentUser = currentUser;
        this.plantRegistry = PlantRegistry.getInstance();
        this.renderer = renderer;
    }

    @Override
    public void execute() {
        Profile profile = currentUser.getProfile();
        CollectionSystem.getInstance().purchasePlant(profile, plantName, renderer);
        DatabaseManager.getInstance().saveAll();
    }
}
