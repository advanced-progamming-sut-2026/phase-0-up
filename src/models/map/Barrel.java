package models.map;

import models.entities.projectiles.Element;

public class Barrel extends Obstacle {

    public Barrel(int hp , double x, int y) {
        super(hp, x, y);
    }

    @Override
    public void takeDamage(int damage, Element element) {
        if (isDestroyed) return;
        this.hp -= damage;
        if (this.hp <= 0) {
            this.hp = 0;
            this.isDestroyed = true;
            System.out.println("Barrel destroyed at X: " + x);
        }
    }
}