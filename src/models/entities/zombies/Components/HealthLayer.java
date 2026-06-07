package models.entities.zombies.Components;

public class HealthLayer {
    private int currentHp;
    private int maxHp;
    private ArmorType type;

    public HealthLayer(int maxHp, ArmorType type) {}
    public int takeDamage(int damage) {return 0;}
    public int getCurrentHp() {return 0;}
    public ArmorType getType() {return null;}
}
