package models.map;

import models.entities.projectiles.Element;

public abstract class Obstacle {
    protected int hp;
    protected double x;
    protected int y;
    protected boolean isDestroyed = false;

    public Obstacle(int hp, double x, int y) {
        this.hp = hp;
        this.x = x;
        this.y = y;
    }

    // متد دمیج خوردن که هر مانع باید به روش خودش پیاده‌سازی کند
    public abstract void takeDamage(int damage, Element element);

    public double getX() { return x; }
    public int getY() { return y; }
    public int getHp() { return hp; }
    public boolean isDestroyed() { return isDestroyed; }
}