package models.entities.zombies.Abilities;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.map.Cell;
import models.map.Row;

// Turquoise: its five-second sun heist IS the charge, then it lasers the four tiles ahead.
public class LaserBeamAbility implements ZombieAbility {
    private static final int TICKS_PER_SECOND = 10;
    private static final int COOLDOWN_TICKS = 5 * TICKS_PER_SECOND;
    private static final int LASER_DAMAGE = 4001;
    private static final int LASER_REACH_TILES = 4;

    // The pause between the heist ending and the aim beginning.
    //
    // 14 ticks is a hair over ZOMBIE_LOSTCITY_CRYSTALSKULL's `power_down` clip (1.267s at 10 Hz), and
    // that is the whole reason it exists. The heist finishes and the beam arms on the SAME tick, so
    // both the wind-down and the shot were announced within a frame of each other -- and ZombieActions
    // gives a zombie one sequence at a time, so the `power_down` already running swallowed the shot's
    // `attack` and it was never drawn.
    //
    // Waiting the wind-down out also puts the four clips in the order they were asked for, and the only
    // order that makes sense: power_up, power, power_down, then attack.
    private static final int ARM_TICKS = 14;

    // And the aim itself: 20 ticks is the `attack` clip's own 1.967 seconds. The beam goes off when the
    // animation ENDS, so the plants die on the frame the light reaches them rather than while the skull
    // is still winding up -- the same construction as SummonGraveAbility.CHANT_TICKS.
    private static final int BEAM_TICKS = 20;

    private static final int NOT_AIMING = -1;

    private int cooldownTimer = 0;
    private int armingTimer = 0;
    private int aimTicks = NOT_AIMING;

    @Override
    public void execute(Zombie zombie) {
        if (zombie == null) {
            return;
        }
        // Mid-aim survives everything except being stopped outright: the check below would abort the
        // shot on the tick the skull's own firing flag went up, because that flag roots it.
        if (aimTicks != NOT_AIMING) {
            advanceAim(zombie);
            return;
        }
        if (zombie.getState().isUnableToMove()) {
            return;
        }
        if (cooldownTimer > 0) {
            cooldownTimer--;
            return;
        }
        if (!zombie.getState().isReadyForLaser()) {
            armingTimer = 0;
            return;
        }
        if (armingTimer < ARM_TICKS) {
            armingTimer++;
            return;
        }
        armingTimer = 0;
        beginAim(zombie);
    }

    private void beginAim(Zombie zombie) {
        aimTicks = 0;
        // Roots the skull for the length of the shot, and tells the view to play `attack`.
        zombie.getState().setFiringLaser(true);
        // Carries the REACH as well as the tile: the beam is lit for the whole of the aim, so the view
        // has to know how far to draw it before the shot resolves. One number, read from the same
        // constant that decides what burns.
        zombie.getGameSession().reportEvent(zombie.getAlias() + " levels its skull at ("
                + (int) zombie.getMovement().getPositionX() + ", "
                + zombie.getMovement().getPositionY() + ") and takes aim down the next "
                + LASER_REACH_TILES + " tiles.");
    }

    private void advanceAim(Zombie zombie) {
        // Frozen or buttered mid-aim: the shot is lost and the skull has to charge again.
        if (zombie.getState().isFrozen() || zombie.getState().isButtered()) {
            aimTicks = NOT_AIMING;
            zombie.getState().setFiringLaser(false);
            zombie.getState().setReadyForLaser(false);
            return;
        }
        aimTicks++;
        if (aimTicks < BEAM_TICKS) {
            return;
        }
        aimTicks = NOT_AIMING;
        zombie.getState().setFiringLaser(false);
        fireLaser(zombie);
        cooldownTimer = COOLDOWN_TICKS;
        // Clearing the flag disarms the beam and lets StealSunAbility line up the next heist.
        zombie.getState().setReadyForLaser(false);
    }

    private void fireLaser(Zombie zombie) {
        int row = zombie.getMovement().getPositionY();
        double zombieX = zombie.getMovement().getPositionX();
        Row r = zombie.getGameSession().getMap().getRow(row);
        if (r == null) {
            return;
        }
        double reachLimit = zombieX - LASER_REACH_TILES;
        for (Cell cell : r.getCells()) {
            Plant p = cell.getCurrentPlant();
            if (p != null && !p.isDead() && cell.getX() < zombieX && cell.getX() >= reachLimit
                    && p.getHealth() != null) {
                p.getHealth().takeDamage(LASER_DAMAGE);
            }
        }
        burnHypnotised(zombie, r, zombieX, reachLimit);
        zombie.getGameSession().reportEvent(zombie.getAlias() + " fires a laser beam through the next "
                + LASER_REACH_TILES + " tiles of lane " + row + " from (" + (int) zombieX + ", "
                + row + ").");
    }

    // A hypnotised zombie standing in the beam burns with the plants.
    //
    // It is fighting for the player and it is in the way of a beam that is destroying everything in
    // four tiles of lane; sparing it would make the one thing in the path that is not a plant also the
    // only thing that survives. The All-Star and the Arcade Zombie already treat a charmed ally as one
    // more obstacle, and this is the same rule with light instead of a shoulder.
    private void burnHypnotised(Zombie zombie, Row row, double zombieX, double reachLimit) {
        if (row.getZombies() == null) {
            return;
        }
        for (Zombie other : new java.util.ArrayList<>(row.getZombies())) {
            if (other == zombie || other.getHealth() == null || other.getHealth().isDead()
                    || !other.getState().isHypnotized()) {
                continue;
            }
            double otherX = other.getMovement().getPositionX();
            if (otherX < zombieX && otherX >= reachLimit) {
                other.getHealth().applyDamage(LASER_DAMAGE, null, null);
            }
        }
    }
}
