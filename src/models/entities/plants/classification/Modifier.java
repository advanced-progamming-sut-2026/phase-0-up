package models.entities.plants.classification;

import models.entities.plants.Plant;
import models.entities.plants.components.PlantHealthComponent;
import models.entities.zombies.Zombie;

public class Modifier extends Plant {
    public Modifier(String name, int id, double x, int y, PlantHealthComponent health, int level, int cost, boolean isAquatic) {
        super(name, id, x, y, health, level, cost, isAquatic);
    }

    public void applyEffect(Plant targetPlant , Zombie targetZombie){}
}
