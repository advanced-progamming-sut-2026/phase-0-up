package models.entities.collectibles;

import models.entities.Entity;
import models.game.GameSession;

public abstract class Collectible extends Entity {
    protected int expireTicks;
    protected boolean isCollected;
    protected boolean isExpired;

    public Collectible(double x, int y, int expireTicks) {
        this.x = x;
        this.y = y;
        this.expireTicks = expireTicks;
        this.isCollected = false;
        this.isExpired = false;
    }

    public void collect(GameSession gameSession) {
        if (!isCollected && !isExpired) {
            this.isCollected = true;
            applyEffect(gameSession);
        }
    }

    protected abstract void applyEffect(GameSession gameSession);

    public boolean isRemovable() {
        return isCollected || isExpired;
    }

    @Override
    public void update(GameSession gameSession) {
        if (!isCollected && !isExpired) {
            if (expireTicks > 0) {
                expireTicks--;
            } else {
                isExpired = true;
            }
        }
    }
}
