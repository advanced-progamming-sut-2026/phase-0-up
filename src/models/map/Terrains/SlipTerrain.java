package models.map.Terrains;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;

public class SlipTerrain extends Terrain{
    private SlipDirection direction;

    public SlipTerrain(SlipDirection direction) {
        this.plantable = false;
        this.direction = direction;
        this.symbol = '%';
    }

    @Override
    public void effect(Zombie z, Plant p) {
        if(direction == SlipDirection.UP){
            z.getMovement().startLaneSwitch(z.getMovement().getPositionY()-1);
        } else {
            z.getMovement().startLaneSwitch(z.getMovement().getPositionY()+1);
        }
    }
}
