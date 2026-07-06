package models.entities.plants.abilities.triggers;

import models.entities.plants.Plant;
import models.game.GameSession;

public interface TriggerStrategy {
    boolean canTrigger(Plant owner, GameSession gameSession);
}