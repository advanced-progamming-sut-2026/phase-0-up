package models.entities.zombies.Components;

public class HealthLayer {
    private int currentHp;
    private int maxHp;
    private ArmorType type;

    public HealthLayer(ArmorType type) {
        this(type, type.getHp());
    }

    // Used for the base body, whose HP comes from the zombie's own Hitpoints (from JSON) rather
    // than the fixed enum value.
    public HealthLayer(ArmorType type, int hp) {
        this.maxHp = hp;
        this.type = type;
        this.currentHp = hp;
    }
    public int takeDamage(int damage) {
        int absorbed = Math.min(damage, currentHp);
        currentHp -= absorbed;
        return absorbed;
    }
    // Scales this layer's HP (Hypno-shroom's ZOMBIE_HEALTH_MULTIPLIER upgrade on a hypnotized ally).
    public void scale(double multiplier) {
        this.maxHp = (int) Math.round(maxHp * multiplier);
        this.currentHp = (int) Math.round(currentHp * multiplier);
    }

    public int getCurrentHp() {return currentHp;}
    public ArmorType getType() {return type;}
}
