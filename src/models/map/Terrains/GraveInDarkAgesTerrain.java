package models.map.Terrains;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;

public class GraveInDarkAgesTerrain extends GraveTerrain{
    private GravesInDarkAgesTypes type;

    public GraveInDarkAgesTerrain(GravesInDarkAgesTypes type) {
        this.type = type;
        this.symbol = '?';
        this.hp = 700;
    }

    @Override
    public void getShot(int damage){
        hp -= damage;
        if(hp<= 0){
            hp = 0;
            isDead = true;
            dropCollectibles();
        }
    }

    private void dropCollectibles() {
        
    }

    @Override
    public void effect(Zombie z, Plant p) {

    }
}
