package models.map.Terrains;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;

public class NecromancyTerrain extends Terrain{
    public Zombie spawnFromGrave(){return null;}


    @Override
    public void effect(Zombie z, Plant p) {

    }
}
