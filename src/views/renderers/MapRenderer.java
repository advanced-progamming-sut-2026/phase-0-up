package views.renderers;

import models.entities.zombies.Zombie;
import models.game.GameSession;
import models.map.Cell;
import models.map.GameMap;

import java.util.List;

public class MapRenderer {
    public void renderAllTheMap(GameMap activeMap , GameSession activeSession){}
    public void renderAllZombies(List<Zombie> activeZombies){}
    public void renderAllPlants(List<Cell> cells){}
    public void renderGameSession(GameSession activeSession){}
}
