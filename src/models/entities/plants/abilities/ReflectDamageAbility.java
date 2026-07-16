package models.entities.plants.abilities;

import models.entities.plants.Plant;
import models.entities.projectiles.Element;
import models.entities.zombies.Zombie;
import models.game.GameSession;

// Reflects damage onto any zombie that bites the plant (Endurian). Driven by the on-eaten hook, so it fires per bite.
public class ReflectDamageAbility extends PlantAbility {
    private int reflectDamage;

    public ReflectDamageAbility(int reflectDamage) {
        super(0, null); // reacts to being eaten, not the tick loop
        this.reflectDamage = reflectDamage;
    }

    @Override
    public void execute(Plant owner, GameSession gameSession) {
        // no tick behavior
    }

    @Override
    public void onOwnerEaten(Plant owner, Zombie eater, GameSession gameSession) {
        eater.getHealth().applyDamage(reflectDamage, Element.NEUTRAL, owner);
    }

    // Plant food: permanently increases reflected damage (Endurian).
    public void boostReflect(int amount) {
        this.reflectDamage += amount;
    }
}
