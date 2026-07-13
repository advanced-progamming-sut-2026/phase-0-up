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
            extinguishDynamite();
            return;
        }
        tickCounter++;
        if (tickCounter >= EXPLOSION_DELAY_TICKS) {
            triggerExplosionAndJump(zombie);
        }
    }

    private void extinguishDynamite() {
        this.isLit = false;
        System.out.println("Dynamite extinguished by ice!");
    }

    private void triggerExplosionAndJump(Zombie zombie) {
        this.hasExploded = true;

        zombie.getMovement().setPositionX(0.0);

        double currentSpeed = zombie.getMovement().getSpeed();
        zombie.getMovement().setSpeed(-currentSpeed);

        System.out.println("Dynamite exploded! Prospector jumped to X=0 and reversed direction.");
    }

    public boolean isLit() {
        return isLit;
    }
}