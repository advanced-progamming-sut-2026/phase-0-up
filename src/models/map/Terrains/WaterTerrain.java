package models.map.Terrains;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;

// Marks a flooded beach tile. The real "is this cell underwater" flag lives on the Cell (isFlooded),
// which the planting rules and the tide system read; this terrain is just the marker that a tile is
// water rather than sand. The tide (EnvironmentSystem) adds and removes these markers as the waterline
// moves.
//
// The old per-terrain water-level bookkeeping was removed: it was never called and was broken (an
// infinite/empty loop in the drain path, and an NPE on any empty flooded cell). Tides now live in one
// place -- EnvironmentSystem -- with the waterline tracked on the GameMap.
public class WaterTerrain extends Terrain {
    public WaterTerrain() {
        this.plantable = true;   // a Lily Pad or an aquatic plant may still sit here
        this.symbol = '!';
    }

    @Override
    public void effect(Zombie z, Plant p) {
    }
}
