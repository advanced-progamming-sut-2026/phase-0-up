package models.templates;

import models.entities.zombies.Components.ArmorType;

import java.util.List;

// Immutable blueprint for one zombie type, distilled from data/zombie-data/zombies.json. Kept to the
// core combat stats plus the armor stack and the objclass (which drives the behavior/ability set);
// behavior-specific parameters live in the individual ability classes with sensible defaults.
public class ZombieTemplate {
    private final String alias;
    private final String objclass;
    private final int baseHp;
    private final double speed;
    private final int eatDps;
    private final int wavePointCost;
    private final boolean canSpawnPlantFood;
    private final List<ArmorType> armors;

    public ZombieTemplate(String alias, String objclass, int baseHp, double speed, int eatDps,
                          int wavePointCost, boolean canSpawnPlantFood, List<ArmorType> armors) {
        this.alias = alias;
        this.objclass = objclass;
        this.baseHp = baseHp;
        this.speed = speed;
        this.eatDps = eatDps;
        this.wavePointCost = wavePointCost;
        this.canSpawnPlantFood = canSpawnPlantFood;
        this.armors = armors;
    }

    public String getAlias() { return alias; }
    public String getObjclass() { return objclass; }
    public int getBaseHp() { return baseHp; }
    public double getSpeed() { return speed; }
    public int getEatDps() { return eatDps; }
    public int getWavePointCost() { return wavePointCost; }
    public boolean isCanSpawnPlantFood() { return canSpawnPlantFood; }
    public List<ArmorType> getArmors() { return armors; }
}
