package models.map.Terrains;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;

// The hole a Doom-shroom leaves behind.
//
// A Doom-shroom clears nearly the whole board, and the price for that in the real game is the ground
// it stood on: the tile is scorched and nothing can be planted there for the rest of the level. Here
// it detonated and left the lawn exactly as it found it, which made the biggest bomb in the game
// strictly better than every other one.
//
// Unplantable but otherwise inert: shots fly over it, zombies walk across it, and it never expires.
// Cell.isPlantable() already refuses any tile carrying a terrain that says it is not plantable and is
// not destroyed, so the whole rule is the two lines in the constructor.
public class CraterTerrain extends Terrain {

    public CraterTerrain() {
        this.plantable = false;
        // Distinct from the grave 'G' and the ice '#': the terminal map has to show this too, and a
        // crater that shared a symbol with something removable would read as removable.
        this.symbol = 'o';
    }

    @Override
    public void effect(Zombie z, Plant p) {
        // Nothing happens here. A crater is a hole in the ground, not a trap.
    }
}
