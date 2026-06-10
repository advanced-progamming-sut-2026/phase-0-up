package models.templates;

import java.util.List;

public class PlantTemplate {
    private int id;
    private String name;
    private String category;
    private List<String> tags;
    private int cost;
    private int baseHp;
    private int attackDamage;
    private int fireRate; //how many bullets are shot
    private int sunProductionRate;
    private String baseAbility;
    private String plantFoodEffect;
    private String[] upgrades;
    private int actionInterval;
    private int recharge;
}
