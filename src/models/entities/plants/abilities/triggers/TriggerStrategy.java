package models.entities.plants.abilities.triggers;

import models.entities.plants.Plant;
import models.game.GameSession;

// Every implementation filters candidate zombies through Zombie.isTargetable(), which is the one
// place that decides whether a zombie is alive and actually standing on the grid.
public interface TriggerStrategy {
    boolean canTrigger(Plant owner, GameSession gameSession);
}
