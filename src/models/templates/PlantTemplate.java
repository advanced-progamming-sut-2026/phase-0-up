package models.templates;

import models.entities.plants.abilities.ShootDirection;
import models.entities.plants.abilities.TargetingPriority;
import models.entities.projectiles.Element;
import models.entities.projectiles.ProjectileType;
import models.entities.projectiles.Trajectory;

import java.util.List;

// Immutable blueprint for one plant, deserialized straight from data/plants.json by Gson.
// It carries only data (no game logic); PlantFactory reads it to build live Plant instances.
// Fields mirror the JSON keys one-to-one so Gson can populate them by reflection.
public class PlantTemplate {
    private int id;
    private String name;
    private PlantCategory category;
    private List<String> tags;
    private int cost;
    private int baseHp;
    private int damage;
    private int lifespan;
    private double actionInterval;
    private double recharge;
    private AbilityType abilityType;
    private double abilityValue;
    private AbilityParams abilityParams;
    private List<ExtraAbilitySpec> extraAbilities;
    private List<PlantFoodSpec> plantFood;
    private List<UpgradeSpec> upgrades;
    private boolean isProtector;
    private boolean isPlatform;

    public int getId() { return id; }
    public String getName() { return name; }
    public PlantCategory getCategory() { return category; }
    public List<String> getTags() { return tags; }
    public int getCost() { return cost; }
    public int getBaseHp() { return baseHp; }
    public int getDamage() { return damage; }
    // Limited lifespan in seconds (Sea/Puff-shroom); 0 means the plant lives indefinitely.
    public int getLifespan() { return lifespan; }
    public double getActionInterval() { return actionInterval; }
    public double getRecharge() { return recharge; }
    public AbilityType getAbilityType() { return abilityType; }
    public double getAbilityValue() { return abilityValue; }
    public AbilityParams getAbilityParams() { return abilityParams; }
    public List<ExtraAbilitySpec> getExtraAbilities() { return extraAbilities; }
    public List<PlantFoodSpec> getPlantFood() { return plantFood; }
    public List<UpgradeSpec> getUpgrades() { return upgrades; }
    public boolean isProtector() { return isProtector; }
    public boolean isPlatform() { return isPlatform; }

    // Superset of every "abilityParams" key seen across all plants. Any given plant fills only the
    // subset its ability needs; the rest keep their Java defaults (0 / 0.0 / null). Object types are
    // used where a missing value must be distinguishable from a real zero (e.g. splashRadiusX).
    public static class AbilityParams {
        private ProjectileType projectileType;
        private Element element;
        private Trajectory trajectory;
        private ShootDirection direction;
        private int shotCount;
        private double speedX;
        private int burstDelayTicks;
        private int pierceCount;
        private double maxRange;
        private int splashDamage;
        private double splashRadiusX;
        private int splashRowRadius;

        private int[] rowOffsets;
        private double[][] directionSpeeds;

        private int[] sunAmountsByStage;
        private int[] stageUpTicks;
        private double doubleSunChance;
        private int spawnCount;
        private int sunAmount;

        private int[] damageByStage;
        private int[] rowRadiusByStage;
        private int[] colRadiusByStage;

        private TargetingPriority priorityStrategy;
        private double priorityRange;

        private int cyanReloadTicks;
        private int blueReloadTicks;
        private int orangeReloadTicks;

        private int kernelDamage;
        private int butterDamage;

        private int freezeDurationTicks;
        private int explosionRowRadius;
        private int explosionColRadius;
        private int reflectDamage;
        private double range;
        private int damageMultiplier;
        private int rowRadius;
        private int colRadius;

        public ProjectileType getProjectileType() { return projectileType; }
        public Element getElement() { return element; }
        public Trajectory getTrajectory() { return trajectory; }
        public ShootDirection getDirection() { return direction; }
        public int getShotCount() { return shotCount; }
        public double getSpeedX() { return speedX; }
        public int getBurstDelayTicks() { return burstDelayTicks; }
        public int getPierceCount() { return pierceCount; }
        public double getMaxRange() { return maxRange; }
        public int getSplashDamage() { return splashDamage; }
        public double getSplashRadiusX() { return splashRadiusX; }
        public int getSplashRowRadius() { return splashRowRadius; }
        public int[] getRowOffsets() { return rowOffsets; }
        public double[][] getDirectionSpeeds() { return directionSpeeds; }
        public int[] getSunAmountsByStage() { return sunAmountsByStage; }
        public int[] getStageUpTicks() { return stageUpTicks; }
        public double getDoubleSunChance() { return doubleSunChance; }
        public int getSpawnCount() { return spawnCount; }
        public int getSunAmount() { return sunAmount; }
        public int[] getDamageByStage() { return damageByStage; }
        public int[] getRowRadiusByStage() { return rowRadiusByStage; }
        public int[] getColRadiusByStage() { return colRadiusByStage; }
        public TargetingPriority getPriorityStrategy() { return priorityStrategy; }
        public double getPriorityRange() { return priorityRange; }
        public int getCyanReloadTicks() { return cyanReloadTicks; }
        public int getBlueReloadTicks() { return blueReloadTicks; }
        public int getOrangeReloadTicks() { return orangeReloadTicks; }
        public int getKernelDamage() { return kernelDamage; }
        public int getButterDamage() { return butterDamage; }
        public int getFreezeDurationTicks() { return freezeDurationTicks; }
        public int getExplosionRowRadius() { return explosionRowRadius; }
        public int getExplosionColRadius() { return explosionColRadius; }
        public int getReflectDamage() { return reflectDamage; }
        public double getRange() { return range; }
        public int getDamageMultiplier() { return damageMultiplier; }
        public int getRowRadius() { return rowRadius; }
        public int getColRadius() { return colRadius; }
    }

    // A secondary ability layered onto the plant (Split Pea's backward shots, Wasabi Whip's warmth).
    public static class ExtraAbilitySpec {
        private AbilityType abilityType;
        private double actionInterval;
        private AbilityParams abilityParams;

        public AbilityType getAbilityType() { return abilityType; }
        public double getActionInterval() { return actionInterval; }
        public AbilityParams getAbilityParams() { return abilityParams; }
    }

    // One sub-effect of a plant's plant-food. "value" is optional in the JSON (defaults to 0).
    public static class PlantFoodSpec {
        private PlantFoodType type;
        private double value;

        public PlantFoodType getType() { return type; }
        public double getValue() { return value; }
    }

    // One per-level upgrade. specialTag is meaningful only when type == SPECIAL_MECHANIC.
    public static class UpgradeSpec {
        private int level;
        private UpgradeType type;
        private double value;
        private String specialTag;

        public int getLevel() { return level; }
        public UpgradeType getType() { return type; }
        public double getValue() { return value; }
        public String getSpecialTag() { return specialTag; }
    }
}
