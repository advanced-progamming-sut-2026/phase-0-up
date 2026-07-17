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
    private boolean hasOctopus = false;
    private int octopusHp = 0;
    protected List<PlantTags> tags;
    private boolean isCat = false;
    private Zombie cursedByWizard = null;

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

    //while frozen in ice, trapped by an octopus, or cursed into a cat the plant is incapacitated:
    //its abilities and auto plant-food do nothing until it is freed (see damageIceBlock/damageOctopus/revertFromCat).
        if (isDisabled()) {
            return;
        }

    //each component updates its cooldown and if cooldown is finished it executes the ability.
        if (abilities != null) {
            for (PlantAbility ability : abilities) {
                ability.update(this, gameSession);
            }
        }

        updateAutoPlantFood(gameSession);
    }

    // A plant is incapacitated while frozen solid, trapped by an octopus, or cursed into a cat.
    public boolean isDisabled() {
        return isFrozen || hasOctopus || isCat;
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

    // Chill accumulates in three levels; the third one freezes the plant solid in a 600-HP ice block.
    // This is what a Frostbite freezing wind and a Hunter's ice throw feed into.
    public void takeIceHit() {
        if (isFrozen) return;

        this.iceHits++;
        if (this.iceHits >= 3) {
            freezePlant();
        }
    }

    public int getIceHits() { return iceHits; }
    public int getChillLevel() { return isFrozen ? 3 : iceHits; }
    public int getIceBlockHp() { return iceBlockHp; }

    // A plant counts as a "fire plant" for melting nearby ice if it shoots a FIRE projectile.
    public boolean isFirePlant() {
        for (PlantAbility ability : abilities) {
            if (ability instanceof models.entities.plants.abilities.ShootProjectileAbility
                    && ((models.entities.plants.abilities.ShootProjectileAbility) ability).getElement()
                        == models.entities.projectiles.Element.FIRE) {
                return true;
            }
        }
        return false;
    }

    private void freezePlant() {
        this.isFrozen = true;
        this.iceBlockHp = Constants.FROZEN_HP;   // 600, matching the Frostbite ice-block rule
        System.out.println(this.getName() + " is completely frozen in ice!");
    }

    // Fire wipes an ice block out in one hit (a fire projectile, or heat); anything else chips at its
    // 600 HP. When it breaks, the plant is free and its chill resets.
    public void damageIceBlock(int damage, models.entities.projectiles.Element element) {
        if (!isFrozen) return;

        if (element == models.entities.projectiles.Element.FIRE) {
            this.iceBlockHp = 0;
        } else {
            this.iceBlockHp -= damage;
        }

        if (this.iceBlockHp <= 0) {
            this.isFrozen = false;
            this.iceHits = 0;
            this.iceBlockHp = 0;
            System.out.println("Ice block broken! " + this.getName() + " is free!");
        }
    }

    // Passive melt from a neighbouring fire plant (60 HP/s). Never a fire element, so it always chips
    // rather than instantly clearing.
    public void meltIceBlock(int amount) {
        damageIceBlock(amount, models.entities.projectiles.Element.NEUTRAL);
    }

    public void bindWithOctopus() {
        if (hasOctopus || isDead()) return;

        this.hasOctopus = true;
        this.octopusHp = 200;
        System.out.println(this.getName() + " was trapped by an octopus!");
    }

    public void damageOctopus(int damage) {
        if (!hasOctopus) return;

        this.octopusHp -= damage;
        if (this.octopusHp <= 0) {
            this.hasOctopus = false;
            this.octopusHp = 0;
            System.out.println("Octopus destroyed! " + this.getName() + " is free!");
        }
    }

    public boolean hasOctopus() { return hasOctopus; }

    // Note: abilities are gated centrally in update() via isDisabled() (frozen / octopus / cat),
    // so individual shoot / produce-sun abilities do not need their own guard.

    public void turnIntoCat(Zombie wizard) {
        if (isCat || isDead()) return;

        this.isCat = true;
        this.cursedByWizard = wizard;
        System.out.println(this.getName() + " was turned into a sheep/cat by Wizard!");
    }

    public void revertFromCat() {
        if (!isCat) return;

        this.isCat = false;
        this.cursedByWizard = null;
        System.out.println(this.getName() + " reverted back to normal!");
    }

    public boolean isCat() { return isCat; }
    public Zombie getCursedByWizard() { return cursedByWizard; }
}