package models.entities.plants.abilities;

import models.entities.plants.Plant;
import models.entities.plants.abilities.triggers.TriggerStrategy;
import models.entities.zombies.Zombie;
import models.game.GameSession;

public abstract class PlantAbility {
    protected int cooldownTimer;
    protected int actionInterval;

    protected TriggerStrategy triggerStrategy;

    public PlantAbility(int actionInterval, TriggerStrategy triggerStrategy) {
        this.actionInterval = actionInterval;
        this.cooldownTimer = actionInterval;
        this.triggerStrategy = triggerStrategy;
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

    public boolean canExecute(Plant owner,  GameSession gameSession){
        if (triggerStrategy == null) return false;

        return triggerStrategy.canTrigger(owner, gameSession);
    }
    public abstract void execute(Plant owner,  GameSession gameSession);

    // Whether this ability has committed to acting but its effect has not landed yet -- the plant is
    // visibly drawing back. Abilities that take effect the instant they trigger are never winding up.
    //
    // A wind-up costs the simulation nothing: the ability still fires once per actionInterval, so only
    // the moment WITHIN the cycle shifts. What it buys is a window the view can play an animation in,
    // ending on the effect rather than starting after it.
    public boolean isWindingUp() {
        return false;
    }

    // Called once when the owning plant dies, before removal. Default: no death behavior.
    public void onOwnerDeath(Plant owner, GameSession gameSession) {
        // no-op
    }

    // Called each time a zombie lands a bite on the owning plant. Default: no on-eaten behavior.
    public void onOwnerEaten(Plant owner, Zombie eater, GameSession gameSession) {
        // no-op
    }
}
