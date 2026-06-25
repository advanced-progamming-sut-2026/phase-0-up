package controllers.commands.collection;

import controllers.commands.Command;
import models.user.Profile;
import models.user.User;
import utils.registry.PlantRegistry;
import utils.registry.ZombieRegistry;
import views.renderers.MenuRenderer.CollectionMenuRenderer;

public class ShowEntityDetailsCommand implements Command {
    private ZombieRegistry zombieRegistry;
    private PlantRegistry plantRegistry;
    private ShowListType type;
    private String entityName;
    private CollectionMenuRenderer renderer;

    public ShowEntityDetailsCommand(ShowListType type, String entityName, CollectionMenuRenderer renderer) {
        this.type = type;
        this.entityName = entityName;
        this.plantRegistry = PlantRegistry.getInstance();
        this.zombieRegistry = ZombieRegistry.getInstance();
    }

    @Override
    public void execute() {
        if(type == ShowListType.ZOMBIES || type == ShowListType.ALL_ZOMBIES){
            renderer.renderZombieDetails(zombieRegistry, entityName);
        }else {
            renderer.renderPlantDetails(plantRegistry, entityName);
        }
    }
}
