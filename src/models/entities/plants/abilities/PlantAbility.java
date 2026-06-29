package models.entities.plants.abilities;

import models.entities.plants.Plant;
import models.game.GameSession;

public abstract class PlantAbility {
    protected int cooldownTimer;
    protected int actionInterval;

    public PlantAbility(int actionInterval) {
        this.actionInterval = actionInterval;
        this.cooldownTimer = actionInterval;
    }

    public void update(Plant owner, GameSession gameSession){
        if (cooldownTimer > 0) {
            cooldownTimer--;
        }

        if (cooldownTimer <= 0){
            if (canExecute(owner,  gameSession)) {
                execute(owner, gameSession);
                cooldownTimer = actionInterval;
            }
        }
    }

    public abstract boolean canExecute(Plant owner,  GameSession gameSession);
    public abstract void execute(Plant owner,  GameSession gameSession);
}
