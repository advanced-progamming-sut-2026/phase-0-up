package controllers.commands.collection;

import controllers.commands.Command;
import controllers.systems.CollectionSystem;
import models.user.Profile;
import models.user.User;
import views.renderers.MenuRenderer.CollectionMenuRenderer;

public class UpgradePlantCommand implements Command {
    private String plantName;
    private User currentUser;
    private CollectionMenuRenderer renderer;

    public UpgradePlantCommand(String plantName, User currentUser, CollectionMenuRenderer renderer) {
        this.plantName = plantName;
        this.currentUser = currentUser;
        this.renderer = renderer;
    }

    @Override
    public void execute() {
        Profile profile = currentUser.getProfile();
        CollectionSystem.getInstance().upgradePlant(profile, plantName, renderer);
    }
}
