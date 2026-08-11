package models.entities.collectibles;

import models.entities.Entity;
import models.game.GameSession;

public abstract class Collectible extends Entity {
    protected int expireTicks;
    protected boolean isCollected;
    protected boolean isExpired;

    public Collectible(String name , double x, int y, int expireTicks) {
        super(name , 0, x , y);
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

    // Taken off the board by something other than the player, so applyEffect is deliberately NOT run:
    // the Ra Zombie pockets the sun rather than paying it into the bank. Marking it collected is what
    // makes SunSystem's sweep drop it from the active list.
    public void steal() {
        if (!isCollected && !isExpired) {
            this.isCollected = true;
        }
    }

    protected abstract void applyEffect(GameSession gameSession);

    public boolean isRemovable() {
        return isCollected || isExpired;
    }

    // Ticks left before this vanishes on its own. Purely informational -- the view flashes a sun that
    // is about to time out so the player gets a chance to grab it. A collectible that never expires
    // reports a huge number, which reads naturally as "not soon".
    public int getRemainingTicks() {
        return expireTicks;
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
