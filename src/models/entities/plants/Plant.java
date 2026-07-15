package models.entities.plants;

import models.entities.Entity;
import models.entities.plants.abilities.PlantAbility;
import models.entities.plants.FoodStrategies.CompositePlantFoodStrategy;
import models.entities.plants.components.PlantHealthComponent;
import models.entities.plants.components.StackableComponent;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import utils.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Plant extends Entity {
    protected int level;
    protected int cost;
    private int iceHits = 0;
    private boolean isFrozen = false;
    private int iceBlockHp = 0;
    protected List<PlantTags> tags;

    protected PlantHealthComponent health;

    protected List<PlantAbility> abilities;

    protected boolean thisPlantHasFood;
    protected CompositePlantFoodStrategy plantFoodStrategy;

    protected boolean isAquatic;

    protected StackableComponent  stackableComponent;

    protected boolean isProtector;
    protected boolean isPlatform;
    private boolean deathTriggered;

    protected String category;

    // Upgrade (AUTO_PLANT_FOOD_CHANCE): per-second odds of auto-activating this plant's plant food.
    private double autoPlantFoodChance;
    private int autoFoodTickTimer;
    private final Random random = new Random();

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

    // Fires each ability's on-eaten effect when a zombie bites this plant (Hypno-shroom, Garlic).
    public void onEaten(Zombie eater, GameSession gameSession) {
        if (abilities != null) {
            for (PlantAbility ability : abilities) {
                ability.onOwnerEaten(this, eater, gameSession);
            }
        }
    }

    public boolean isProtector() { return isProtector; }
    public void setProtector(boolean protector) { this.isProtector = protector; }

    public boolean isPlatform() { return isPlatform; }
    public void setPlatform(boolean platform) { this.isPlatform = platform; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }


    public void triggerPlantFood(GameSession gameSession) {
        this.thisPlantHasFood = true;
        if (plantFoodStrategy != null) {
            plantFoodStrategy.executeEffect(this, gameSession);
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

        updateAutoPlantFood(gameSession);
    }

    // Rolls the auto-plant-food chance about once per second (Mega Gatling Pea's level-3 upgrade).
    private void updateAutoPlantFood(GameSession gameSession) {
        if (autoPlantFoodChance <= 0) {
            return;
        }
        autoFoodTickTimer++;
        if (autoFoodTickTimer >= Constants.TICKS_PER_SECOND) {
            autoFoodTickTimer = 0;
            if (random.nextDouble() < autoPlantFoodChance) {
                triggerPlantFood(gameSession);
            }
        }
    }

    public void setAutoPlantFoodChance(double chance) {
        this.autoPlantFoodChance = chance;
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

    public void takeIceHit() {
        if (isFrozen) return;

        this.iceHits++;
        if (this.iceHits >= 3) {
            freezePlant();
        }
    }

    private void freezePlant() {
        this.isFrozen = true;
        this.iceBlockHp = 300;
        System.out.println(this.getName() + " is completely frozen in ice!");
    }

    public void damageIceBlock(int damage) {
        if (!isFrozen) return;

        this.iceBlockHp -= damage;
        if (this.iceBlockHp <= 0) {
            this.isFrozen = false;
            this.iceHits = 0;
            this.iceBlockHp = 0;
            System.out.println("Ice block broken! " + this.getName() + " is free!");
        }
    }
}