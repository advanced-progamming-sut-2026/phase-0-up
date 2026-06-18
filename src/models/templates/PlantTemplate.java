package models.templates;

import java.util.List;

public class PlantTemplate {
    private int id;
    private String name;
    private String category;
    private List<String> tags;
    private int cost;
    private int baseHp;
    private int[] damagePerHeat;
    private int shots;
    private boolean instaKill;
    private int sunProductionRate;
    private String baseAbility;
    private String plantFoodEffect;
    private String[] upgrades;
    private int actionInterval;
    private int recharge;

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public List<String> getTags() {
        return tags;
    }

    public int getCost() {
        return cost;
    }

    public int getBaseHp() {
        return baseHp;
    }


    public int[] getDamagePerHeat() {
        return damagePerHeat;
    }

    public int getShots() {
        return shots;
    }

    public boolean isInstaKill() {
        return instaKill;
    }

    public int getSunProductionRate() {
        return sunProductionRate;
    }

    public String getBaseAbility() {
        return baseAbility;
    }

    public String getPlantFoodEffect() {
        return plantFoodEffect;
    }

    public String[] getUpgrades() {
        return upgrades;
    }

    public int getActionInterval() {
        return actionInterval;
    }

    public int getRecharge() {
        return recharge;
    }
}
