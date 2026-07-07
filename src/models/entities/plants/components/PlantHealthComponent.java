package models.entities.plants.components;

import utils.Constants;

public class PlantHealthComponent {
    private int maxHp;
    private int currentHp;
    private int lifespanTicks;
    private boolean isDead;

    private int poisonDamagePerSecond = 0;
    private int poisonDurationTicks = 0;
    private int poisonTickTimer = 0;

    public PlantHealthComponent(int maxHp) {
        this.maxHp = maxHp;
        this.currentHp = maxHp;
        this.isDead = false;

        this.lifespanTicks = -1; // -1 means this plant doesn't have a limited lifespan
    }

    public void takeDamage(int damage) {
        if (isDead) return;

        this.currentHp -= damage;
        if (this.currentHp <= 0) {
            this.currentHp = 0;
            this.isDead = true;
        }
    }

    public void heal(int amount) {
        if (isDead) return;

        this.currentHp += amount;
        if (this.currentHp > maxHp) {
            this.currentHp = maxHp;
        }
    }

    public boolean isDead() {
        return isDead;
    }

    public void applyPoison(int damagePerTick, int durationInTicks) {
        this.poisonDamagePerSecond = damagePerTick;
        this.poisonDurationTicks = durationInTicks;
        this.poisonTickTimer = 0;
    }

    public void update() {
        if (isDead) return;


        if (lifespanTicks > 0) {
            lifespanTicks--;
            if (lifespanTicks == 0) {
                this.currentHp = 0;
                this.isDead = true;
                return;
            }
        }

        if (poisonDurationTicks > 0) {
            poisonDurationTicks--;
            poisonTickTimer++;

            if (poisonTickTimer >= Constants.TICKS_PER_SECOND) {
                takeDamage(poisonDamagePerSecond);
                poisonTickTimer = 0;
            }
        }
    }

    public int getCurrentHp() { return currentHp; }

    public int getMaxHp() { return maxHp; }

    public void setMaxHp(int maxHp) { this.maxHp = maxHp; }

    public void setLimitedLifespan(int seconds) {
        this.lifespanTicks = seconds * Constants.TICKS_PER_SECOND;
    }
}
