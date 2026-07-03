package models.entities.zombies.Components;

public class HealthLayer {
    private int currentHp;
    private int maxHp;
    private ArmorType type;

    public HealthLayer(int maxHp, ArmorType type) {
        this.maxHp = maxHp;
        this.type = type;
    }
    public int takeDamage(int damage) {
        int absorbed = Math.min(damage, currentHp);
        currentHp -= absorbed;
        return absorbed;
    }
    public int getCurrentHp() {return currentHp;}
    public ArmorType getType() {return type;}
}
