package models.entities.zombies.Abilities;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.map.Cell;
import models.map.Row;

public class ThrowOctopusAbility implements ZombieAbility {
    private int tickCounter = 0;
    private static final int TICKS_PER_SECOND = 10;
    private static final int THROW_COOLDOWN = 8 * TICKS_PER_SECOND;

    // The zombie WINDS UP first, and the octopus leaves its hand when the toss finishes.
    //
    // Same two-moment shape as the Tomb Raiser's chant and the Hunter's snowball, and for the same
    // reason: the plant used to be snared on the tick the cooldown expired, so the octopus was already
    // clinging to it before the zombie had moved an arm.
    //
    // 16 ticks is 1.57 seconds at 10 Hz, which is the frame of ZOMBIE_BEACH_OCTOPUS's `toss` where the
    // octopus actually LEAVES the hand -- roughly halfway through the 3.07s clip, not at the end of it.
    //
    // The release frame is the number that matters, not the clip length: this is a throw, so the arm
    // comes forward, the octopus goes, and the zombie spends the remaining second and a half following
    // through with nothing in its hand. Holding the octopus back to the last frame had it appear after
    // the throwing motion was over. The view is unaffected -- ZombieActions plays the whole clip from
    // its own duration and does not care when the model lets go.
    private static final int TOSS_TICKS = 16;

    private static final int NOT_TOSSING = -1;
    private int tossTicks = NOT_TOSSING;
    // Picked when the wind-up starts, so the throw is aimed at something, and re-checked on release.
    private Plant aimedAt;

    @Override
    public void execute(Zombie zombie) {
        if (zombie == null || zombie.getState().isUnableToMove()) {
            // Held mid-throw: the octopus goes back in the bucket and the cooldown starts again.
            tossTicks = NOT_TOSSING;
            aimedAt = null;
            return;
        }

        if (tossTicks != NOT_TOSSING) {
            advanceToss(zombie);
            return;
        }

        tickCounter++;
        if (tickCounter >= THROW_COOLDOWN) {
            Plant target = findFrontmostFreePlant(zombie);

            if (target != null) {
                tickCounter = 0;
                tossTicks = 0;
                aimedAt = target;
                // What the view listens for to play `toss`. The zombie's OWN tile, because that is the
                // lane the animation has to be claimed in -- nothing is thrown yet.
                zombie.getGameSession().reportEvent(zombie.getAlias() + " winds up an octopus at ("
                        + (int) zombie.getMovement().getPositionX() + ", "
                        + zombie.getMovement().getPositionY() + ").");
            }
        }
    }

    private void advanceToss(Zombie zombie) {
        tossTicks++;
        if (tossTicks < TOSS_TICKS) {
            return;
        }
        Plant target = aimedAt;
        tossTicks = NOT_TOSSING;
        aimedAt = null;
        // A second and a half is long enough for the plant to be dug up, eaten, frozen or grabbed by
        // someone else. A throw at nothing misses -- and says nothing, so no octopus is drawn flying.
        if (target == null || target.isDead() || target.hasOctopus() || target.isFrozen()) {
            return;
        }
        target.bindWithOctopus();
        // BOTH tiles: the view flies the octopus from the thrower's hand to the plant, and the thrower
        // is not in the rest of the sentence anywhere the view could read a position from.
        zombie.getGameSession().reportEvent(zombie.getAlias() + " flings an octopus from ("
                + (int) zombie.getMovement().getPositionX() + ", "
                + zombie.getMovement().getPositionY() + ") onto " + target.getName()
                + " at (" + (int) target.getX() + ", " + target.getY() + ").");
    }

    private Plant findFrontmostFreePlant(Zombie zombie) {
        if (zombie.getMovement() == null || zombie.getGameSession() == null
                || zombie.getGameSession().getMap() == null) {
            return null;
        }

        int rowIdx = zombie.getMovement().getPositionY();
        Row row = zombie.getGameSession().getMap().getRow(rowIdx);
        if (row == null || row.getCells() == null) {
            return null;
        }

        for (int col = 8; col >= 0; col--) {
            Cell cell = row.cellAt(col);
            if (cell != null && cell.hasPlant()) {
                Plant plant = cell.getCurrentPlant();

                if (plant != null && !plant.isDead() && !plant.hasOctopus() && !plant.isFrozen()) {
                    return plant;
                }
            }
        }

        return null;
    }
}