package models.map.Terrains;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;

public class SlipTerrain extends Terrain{
    private SlipDirection direction;

    public SlipTerrain(SlipDirection direction) {
        this.plantable = false;
        this.direction = direction;
        // Match the layout char MapInitializer reads: '^' slides up a row, 'v' slides down.
        this.symbol = direction == SlipDirection.UP ? '^' : 'v';
    }

    // Which way this tile shoves a zombie. That IS the tile's whole rule, and the game ships a separate
    // animation for each direction, so a view that draws one has to ask.
    public SlipDirection getDirection() {
        return direction;
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
