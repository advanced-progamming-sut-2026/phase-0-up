package models.map.Terrains;

import factories.ZombieFactory;
import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import models.map.Cell;

public class NecromancyTerrain extends Terrain{
    private GameSession gameSession;
    private boolean hasGrave;
    private Cell cell;

    public NecromancyTerrain(GameSession gameSession , Cell cell) {
        this.gameSession = gameSession;
        this.cell = cell;
        this.hasGrave = false;
        this.plantable = true;
        this.symbol = '0';
    }

    public void setHasGrave(boolean hasGrave) {
        this.hasGrave = hasGrave;
        if(hasGrave) this.plantable = false;
        else this.plantable = true;
    }

    // Raises a zombie from beneath this tile. The live wave-start path is EnvironmentSystem.applyNecromancy
    // (which also rolls the odds and reports it); this stays as the terrain's own hook and simply guards
    // against a null so it can never drop a null into the row.
    @Override
    public void effect(Zombie z, Plant p) {
        Zombie zombie = ZombieFactory.createZombie("ZombieDefault", cell.getX(), cell.getY(), gameSession);
        if (zombie != null) {
            gameSession.getMap().getRow(cell.getY()).getZombies().add(zombie);
        }
    }
}
