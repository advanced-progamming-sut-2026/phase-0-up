package models.entities.plants;

import models.entities.Entity;
import models.entities.plants.abilities.PlantAbility;
import models.entities.plants.FoodStrategies.CompositePlantFoodStrategy;
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

    public Plant(PlantHealthComponent health, int level, int cost) {
        this.health = health;

        this.cost = cost;
        this.level = level;
        this.tags = new ArrayList<>();
        this.abilities = new ArrayList<>();
        this.thisPlantHasFood = false;
    }

    public void addAbility(PlantAbility ability) {
        this.abilities.add(ability);
    }

    public boolean isDead() {
        return health == null || health.isDead();
    }


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
}