package models.entities.zombies.Abilities;

import models.entities.zombies.Zombie;

public class CarryADynamite implements ZombieAbility {
    private boolean isLit = true;
    private boolean hasExploded = false;

    private int tickCounter = 0;
    private static final int TICKS_PER_SECOND = 10;
    private static final int EXPLOSION_DELAY_TICKS = 10 * TICKS_PER_SECOND;

    // Where the blast puts it: the centre of column 0. Named because the view needs the same number to
    // draw the arc, and it now reads it out of the sentence rather than knowing it independently.
    private static final double LANDING_X = 0.5;

    // The flight itself: 13 ticks, which is blastoff (0.267s) + fly (0.7s) + land (0.333s) at 10 Hz.
    // The model's flight and the three clips ZombieActions plays are therefore the same 1.3 seconds by
    // construction -- change one and the zombie either lands before it has finished falling or hangs in
    // the air after it has already touched down.
    private static final int FLIGHT_TICKS = 13;
    private static final int NOT_FLYING = -1;

    private int flightTicks = NOT_FLYING;
    private double launchX;

    @Override
    public void execute(Zombie zombie) {
        // Above every other check, including the can-it-move one: being in the air is exactly the sort
        // of thing that reads as "unable to move", and a flight that aborted on its own first tick
        // would leave the zombie hanging over the lawn forever.
        if (flightTicks != NOT_FLYING) {
            advanceFlight(zombie);
            return;
        }
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

        launchX = zombie.getMovement().getPositionX();
        flightTicks = 0;
        // In the air: nothing can hit it, it cannot bite anything, and MovementComponent stops walking
        // it -- this ability owns its position until it comes down.
        zombie.getState().setAirborne(true);
        zombie.getState().setFlightProgress(0f);

        // BOTH tiles, because the view draws the flight between them: the blast goes off where the
        // dynamite was and the smoke arcs across to where the zombie comes down.
        zombie.getGameSession().reportEvent("Boom! " + zombie.getAlias() + "'s dynamite explodes at ("
                + fromX + ", " + row + ") and blasts it back to (" + (int) LANDING_X + ", "
                + row + ").");
    }

    // One tick of the arc. The zombie really is between the two tiles for the whole of it: shots pass
    // through it, it eats nothing, and it is drawn along a curve rather than sliding across the ground.
    private void advanceFlight(Zombie zombie) {
        flightTicks++;
        float progress = Math.min(1f, flightTicks / (float) FLIGHT_TICKS);
        zombie.getState().setFlightProgress(progress);
        zombie.getMovement().setPositionX(launchX + (LANDING_X - launchX) * progress);
        if (flightTicks < FLIGHT_TICKS) {
            return;
        }
        land(zombie);
    }

    private void land(Zombie zombie) {
        flightTicks = NOT_FLYING;
        zombie.getState().setAirborne(false);
        zombie.getState().setFlightProgress(0f);
        // Land on the CENTRE of the left-most column, not on x = 0. x <= 0 is the house-breach line that
        // checkLose and the lawn mowers watch, so blasting the zombie to exactly 0 would hand the player
        // an instant loss instead of putting the zombie on column 0 to walk back out.
        zombie.getMovement().setPositionX(LANDING_X);
        double currentSpeed = zombie.getMovement().getSpeed();
        zombie.getMovement().setSpeed(-currentSpeed);
    }

    public boolean isLit() {
        return isLit;
    }
}