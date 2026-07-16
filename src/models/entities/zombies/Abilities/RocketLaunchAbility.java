package models.entities.zombies.Abilities;

import models.entities.zombies.Zombie;

// Prospector: after a countdown it blasts itself backwards toward the house, skipping several columns
// in one leap (once). Mirrors the JSON LaunchCountdown (10s).
public class RocketLaunchAbility implements ZombieAbility {
    private static final int TICKS_PER_SECOND = 10;
    private static final int LAUNCH_COUNTDOWN_TICKS = 10 * TICKS_PER_SECOND;
    private static final double LAUNCH_DISTANCE = 3.0;
    private static final double MIN_X = 0.5;

    private int timer = 0;
    private boolean hasLaunched = false;

    @Override
    public void execute(Zombie zombie) {
        if (zombie == null || hasLaunched || zombie.getState().isUnableToMove()) {
            return;
        }
        timer++;
        if (timer >= LAUNCH_COUNTDOWN_TICKS) {
            double newX = Math.max(MIN_X, zombie.getMovement().getPositionX() - LAUNCH_DISTANCE);
            zombie.getMovement().setPositionX(newX);
            hasLaunched = true;
            System.out.println(zombie.getAlias() + " rocket-jumped toward the house!");
        }
    }
}
