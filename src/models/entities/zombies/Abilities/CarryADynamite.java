package models.entities.zombies.Abilities;

import models.entities.zombies.Zombie;

public class CarryADynamite implements ZombieAbility {
    private boolean isLit = true;
    private boolean hasExploded = false;

    private int tickCounter = 0;
    private static final int TICKS_PER_SECOND = 10;
    private static final int EXPLOSION_DELAY_TICKS = 10 * TICKS_PER_SECOND;

    @Override
    public void execute(Zombie zombie) {
        if (hasExploded || !isLit || zombie.getState().isUnableToMove()) {
            return;
        }

        if (zombie.getState().isChilled() || zombie.getState().isFrozen()) {
            extinguishDynamite(zombie);
            return;
        }
        tickCounter++;
        if (tickCounter >= EXPLOSION_DELAY_TICKS) {
            triggerExplosionAndJump(zombie);
        }
    }

    private void extinguishDynamite(Zombie zombie) {
        this.isLit = false;
        zombie.getGameSession().reportEvent(zombie.getAlias() + "'s dynamite fizzles out in the ice at ("
                + (int) zombie.getX() + ", " + zombie.getY() + ").");
    }

    private void triggerExplosionAndJump(Zombie zombie) {
        this.hasExploded = true;
        int fromX = (int) zombie.getMovement().getPositionX();
        int row = zombie.getMovement().getPositionY();

        zombie.getMovement().setPositionX(0.0);

        double currentSpeed = zombie.getMovement().getSpeed();
        zombie.getMovement().setSpeed(-currentSpeed);

        zombie.getGameSession().reportEvent("Boom! " + zombie.getAlias() + "'s dynamite explodes at ("
                + fromX + ", " + row + ") and blasts it back toward the house.");
    }

    public boolean isLit() {
        return isLit;
    }
}