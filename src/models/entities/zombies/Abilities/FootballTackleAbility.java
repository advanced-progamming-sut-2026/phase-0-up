package models.entities.zombies.Abilities;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.map.Cell;
import models.map.Row;

// The All-Star: charges onto the lawn, flattens the first thing in its way, and plods for the rest of
// its life.
//
// The spec gives it two lines and they describe three phases:
//
//   * it comes on FAST;
//   * anything it runs into -- a plant, or a hypnotised zombie fighting for the player -- takes a
//     lethal hit and is destroyed on the spot, not chewed;
//   * afterwards it carries on at a very slow walk.
//
// ## What it was doing instead
//
// Sprinting whenever the lane ahead happened to be clear and dropping back to normal speed whenever it
// was not -- so it accelerated again after every plant, which is a different zombie entirely. It also
// dealt its 1500 smash EVERY TICK a plant was in reach rather than once, fifteen thousand damage a
// second, and re-announced the tackle each time. And it did not touch hypnotised zombies at all.
//
// The charge is spent once. That is what makes an All-Star a problem to be answered rather than a
// treadmill: it costs the player one plant, and after that it is the slowest thing on the lawn.
public class FootballTackleAbility implements ZombieAbility {

    // How close something has to be to be run into.
    private static final double TACKLE_REACH = 0.6;

    // The charge, and the plod, as multiples of the zombie's own speed.
    //
    // Its base speed in zombies.json is 0.16 -- slower than a Browncoat's 0.185 -- because that figure
    // describes the zombie AFTER its run, which is most of its life. Three times that reads as a sprint
    // next to the horde it arrives with, and four tenths of it is the "very slow" the spec asks for.
    private static final double RUSH_MULTIPLIER = 3.0;
    private static final double PLOD_MULTIPLIER = 0.4;

    private boolean charging = true;
    private double baseSpeed = -1.0;

    @Override
    public void execute(Zombie zombie) {
        if (zombie == null) {
            return;
        }
        if (baseSpeed < 0) {
            baseSpeed = zombie.getMovement().getSpeed();
            startRunning(zombie);
        }
        // Held still -- frozen, or already chewing something -- but still whatever it was. The charge
        // is not spent by being interrupted, so a thawed All-Star finishes the run it started.
        if (zombie.getState().isUnableToMove() || !charging) {
            return;
        }

        Plant plant = plantInFront(zombie);
        if (plant != null) {
            flatten(zombie, plant.getName(), (int) plant.getX(), plant.getY());
            if (plant.getHealth() != null) {
                plant.getHealth().takeDamage(Integer.MAX_VALUE);
            }
            return;
        }
        Zombie ally = hypnotisedInFront(zombie);
        if (ally != null) {
            flatten(zombie, "a hypnotized zombie", (int) ally.getX(), ally.getY());
            ally.getHealth().applyDamage(Integer.MAX_VALUE, null, null);
        }
    }

    private void startRunning(Zombie zombie) {
        zombie.getMovement().setSpeed(baseSpeed * RUSH_MULTIPLIER);
        // What the view reads to draw the `run` clip instead of `walk`. A state rather than an event:
        // running is what this zombie IS for the first part of its life, readable off it on any frame.
        zombie.getState().setRushing(true);
        }

    // The hit, and the end of the charge. Whatever it ran into is destroyed outright -- the spec says
    // "on the spot", so this is not the 1500 SmashDamage in the data being enough to kill most things,
    // it is the blow being lethal by definition.
    private void flatten(Zombie zombie, String what, int col, int row) {
        charging = false;
        zombie.getMovement().setSpeed(baseSpeed * PLOD_MULTIPLIER);
        zombie.getState().setRushing(false);
        zombie.getGameSession().reportEvent(zombie.getAlias() + " tackles " + what
                + " at (" + col + ", " + row + ").");
    }

    private Plant plantInFront(Zombie zombie) {
        int row = zombie.getMovement().getPositionY();
        double zombieX = zombie.getMovement().getPositionX();
        Row r = zombie.getGameSession().getMap().getRow(row);
        if (r == null) {
            return null;
        }
        for (Cell cell : r.getCells()) {
            // The DEFENDING plant, so a Pumpkin over a Peashooter is what gets hit -- the same plant
            // any other zombie would meet first.
            Plant p = cell.getDefendingPlant();
            if (p != null && !p.isDead()) {
                double distance = zombieX - cell.getX();
                if (distance >= 0 && distance <= TACKLE_REACH) {
                    return p;
                }
            }
        }
        return null;
    }

    // A hypnotised zombie is standing in the lane fighting for the player, so as far as an All-Star is
    // concerned it is one more thing to run through. Named separately from the plant because it is a
    // different list to walk and a different way to kill.
    private Zombie hypnotisedInFront(Zombie zombie) {
        int row = zombie.getMovement().getPositionY();
        double zombieX = zombie.getMovement().getPositionX();
        Row r = zombie.getGameSession().getMap().getRow(row);
        if (r == null || r.getZombies() == null) {
            return null;
        }
        for (Zombie other : r.getZombies()) {
            if (other == zombie || other.getHealth() == null || other.getHealth().isDead()
                    || !other.getState().isHypnotized()) {
                continue;
            }
            if (Math.abs(zombieX - other.getMovement().getPositionX()) <= TACKLE_REACH) {
                return other;
            }
        }
        return null;
    }
}
