package models.entities.plants;

import models.entities.Entity;
import models.entities.plants.abilities.PlantAbility;
import models.entities.plants.FoodStrategies.CompositePlantFoodStrategy;
import models.entities.plants.components.PlantHealthComponent;
import models.entities.plants.components.StackableComponent;
import models.game.GameSession;

import java.util.ArrayList;
import java.util.List;

public class Plant extends Entity {
    protected int level;
    protected int cost;

    protected List<PlantTags> tags;

    protected PlantHealthComponent health;

    protected List<PlantAbility> abilities;

    protected boolean thisPlantHasFood;
    protected CompositePlantFoodStrategy plantFoodStrategy;
    protected boolean isFrozen;

    protected boolean isAquatic;

    protected StackableComponent  stackableComponent;

    protected boolean isProtector;
    private boolean deathTriggered;

    public Plant(String name, int id, double x, int y,
                 PlantHealthComponent health, int level, int cost, boolean isAquatic) {

        super(name, id, x, y);

        this.health = health;

        this.cost = cost;
        this.level = level;
        this.tags = new ArrayList<>();
        this.abilities = new ArrayList<>();
        this.thisPlantHasFood = false;
        this.isAquatic =  isAquatic;
    }

    public void addAbility(PlantAbility ability) {
        this.abilities.add(ability);
    }

    public boolean isDead() {
        return health == null || health.isDead();
    }

    // Fires each ability's death effect once (e.g. Explode-o-nut). Call before removing a dead plant.
    public void onDeath(GameSession gameSession) {
        if (deathTriggered) return;
        deathTriggered = true;
        if (abilities != null) {
            for (PlantAbility ability : abilities) {
                ability.onOwnerDeath(this, gameSession);
            }
        }
    }

    public boolean isProtector() { return isProtector; }
    public void setProtector(boolean protector) { this.isProtector = protector; }


    public void triggerPlantFood() {
        this.thisPlantHasFood = true;
        if (plantFoodStrategy != null) {
            plantFoodStrategy.executeEffect(this);
        }
    }

    @Override
    public void update(GameSession  gameSession) {
        if (isDead()) {
            return;
        }

    //updating health for over time damages(like poison)
        if (health != null) {
            health.update();
        }

    //each component updates its cooldown and if cooldown is finished it executes the ability.
        if (abilities != null) {
            for (PlantAbility ability : abilities) {
                ability.update(this, gameSession);
            }
        }
    }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }

    public int getCost() { return cost; }

    public void setPlantFoodStrategy(CompositePlantFoodStrategy strategy) {
        this.plantFoodStrategy = strategy;
    }

    public PlantHealthComponent getHealth() { return health; }

    public List<PlantAbility> getAbilities() {
        return abilities;
    }

    public StackableComponent getStackableComponent() {
        return stackableComponent;
    }

    public void setStackableComponent(StackableComponent stackableComponent) {
        this.stackableComponent = stackableComponent;
    }

    public List<PlantTags> getTags() {
        return tags;
    }

    public boolean isFrozen() {
        return isFrozen;
    }

    public void setFrozen(boolean frozen) {
        isFrozen = frozen;
    }

    public boolean isAquatic() {
        return isAquatic;
    }
}