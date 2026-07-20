package models.map;

import models.entities.projectiles.Element;
import models.game.GameSession;

public class Barrel extends Obstacle {

    public Barrel(int hp , double x, int y) {
        super(hp, x, y);
    }

    public Barrel(int hp, double x, int y, GameSession gameSession) {
        super(hp, x, y, gameSession);
    }

    @Override
    public void takeDamage(int damage, Element element) {
        if (isDestroyed) return;
        this.hp -= damage;
        if (this.hp <= 0) {
            this.hp = 0;
            this.isDestroyed = true;
            report("The barrel is smashed apart at (" + (int) x + ", " + y + ").");
        }
    }
}