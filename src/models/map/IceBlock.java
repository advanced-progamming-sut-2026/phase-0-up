package models.map;

import models.entities.projectiles.Element;
import models.game.GameSession;

public class IceBlock extends Obstacle {

    public IceBlock(int hp ,double x, int y) {
        super(hp, x, y);
    }

    public IceBlock(int hp, double x, int y, GameSession gameSession) {
        super(hp, x, y, gameSession);
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
            report("The ice block is destroyed at (" + (int) x + ", " + y + ").");
        }
    }
}
