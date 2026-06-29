package models.entities.plants;

public class PlantHealthComponent {
    private int maxHp;
    private int currentHp;

    private int poisonDamagePerTick = 0;
    private int poisonDurationTicks = 0;

    public PlantHealthComponent(int maxHp) {
        this.maxHp = maxHp;
        this.currentHp = maxHp;
    }

    public void takeDamage(int damage) {
        this.currentHp -= damage;
        if (this.currentHp < 0) {
            this.currentHp = 0;
        }
    }

    public void heal(int amount) {
        this.currentHp += amount;
        if (this.currentHp > maxHp) {
            this.currentHp = maxHp;
        }
    }

    public boolean isDead() {
        return this.currentHp <= 0;
    }

    public void applyPoison(int damagePerTick, int durationInTicks) {
        this.poisonDamagePerTick = damagePerTick;
        this.poisonDurationTicks = durationInTicks;
    }

    public void update() {
        if (poisonDurationTicks > 0) {
            takeDamage(poisonDamagePerTick);
            poisonDurationTicks--;
        }
    }

    public int getCurrentHp() { return currentHp; }
    public int getMaxHp() { return maxHp; }
    public void setMaxHp(int maxHp) { this.maxHp = maxHp; }
}
