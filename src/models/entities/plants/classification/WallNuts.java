package models.entities.plants.classification;

import models.entities.plants.Plant;
import models.entities.plants.components.PlantHealthComponent;

public class WallNuts extends Plant {
    private int armorHp;
    private boolean blocksJumpers;
    private boolean isArmor;

    public WallNuts(String name, int id, double x, int y, PlantHealthComponent health, int level, int cost, boolean isAquatic) {
        super(name, id, x, y, health, level, cost, isAquatic);
    }
}
