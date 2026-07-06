package models.entities.plants.abilities.triggers;

import models.entities.plants.Plant;
import models.game.GameSession;

public class AlwaysTrueTrigger implements TriggerStrategy{
    @Override
    public boolean canTrigger(Plant owner, GameSession gameSession) {
        return true;
    }
}
