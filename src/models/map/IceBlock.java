package models.map;

import models.entities.projectiles.Element;

public class IceBlock extends Obstacle {

    public IceBlock(int hp ,double x, int y) {
        super(hp, x, y);
    }

    @Override
    public void takeDamage(int damage, Element element) {
        if (isDestroyed) return;
        if (element == Element.ICE) {
            return;
        }
        int actualDamage = (element == Element.FIRE) ? (damage * 2) : damage;

        this.hp -= actualDamage;
        if (this.hp <= 0) {
            this.hp = 0;
            this.isDestroyed = true;
            System.out.println("Ice block destroyed at X: " + x);
        }
    }
}
