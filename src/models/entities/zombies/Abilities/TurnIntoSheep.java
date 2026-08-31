package models.entities.zombies.Abilities;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.map.Cell;
import models.map.Row;

// The Wizard Zombie's hex: it does not eat plants, it turns them into sheep.
//
// ## Sheep, not cats
//
// The spec calls the result a cat; the game calls it a sheep, and so does every piece of art that
// ships for it -- the wizard's own casting clip is named `sheep` and the effect is
// DARK_WIZARD_SHEEPENING. Same mechanic either way (the plant is disabled, zombies walk past it, and
// it comes back when the caster dies), so the shipped art wins and the name follows the art.
//
// ## What was wrong with it
//
// The adjacency test read `Math.abs(zX - cell.getX()) <= 35.0` -- 35 TILES on a nine-column board, so
// it matched every plant in the lane. The wizard hexed the entire row on its first tick, the cooldown
// branch below was unreachable, and no event was ever reported, so nothing on screen changed either.
// The same 35.0 that IgnoreObstaclesAbility was caught with; reach here is in tiles.
public class TurnIntoSheep implements ZombieAbility {

    private static final int TICKS_PER_SECOND = 10;

    // The game hexes the first plant 7-9 seconds after the wizard walks on, and one every 11-14 after
    // that. Taken as fixed 8 and 12 rather than rolled: this project keeps its runs reproducible (the
    // scoring mode seeds a whole day from a date, and the screenshot harness replays input), so a
    // random cast interval would be one more thing making two identical runs differ.
    private static final int FIRST_SPELL_TICKS = 8 * TICKS_PER_SECOND;
    private static final int SPELL_COOLDOWN = 12 * TICKS_PER_SECOND;

    // The wizard raises its staff first and the plant changes when the spell lands. 23 ticks is the
    // length of ZOMBIE_DARK_WIZARD's `sheep` clip, 2.3 seconds at 10 Hz -- the same construction as
    // SummonGraveAbility.CHANT_TICKS: change one and the other has to move with it.
    private static final int CAST_TICKS = 23;

    // A plant the wizard has walked right up to, in tiles. It hexes what it reaches instead of eating
    // it, which is what the spec asks for, and the ranged cast below is what the game adds on top.
    private static final double REACH_TILES = 0.5;

    private int tickCounter = 0;
    private static final int NOT_CASTING = -1;
    private int castTicks = NOT_CASTING;
    private Plant aimedAt;

    @Override
    public void execute(Zombie wizard) {
        if (wizard == null || wizard.getState().isUnableToMove()) {
            // Interrupted: the staff comes down and the spell is lost.
            castTicks = NOT_CASTING;
            aimedAt = null;
            return;
        }

        if (castTicks != NOT_CASTING) {
            advanceCast(wizard);
            return;
        }

        tickCounter++;
        Plant target = pickTarget(wizard);
        if (target != null) {
            beginCast(wizard, target);
        }
    }

    // What to hex next, or null for "nothing yet".
    //
    // A plant it has walked into is taken immediately -- that is the wizard refusing to eat, and it
    // should not have to wait out a cooldown to deal with something already under its nose. Otherwise
    // it reaches down the lane, once the timer is up.
    private Plant pickTarget(Zombie wizard) {
        Plant adjacent = getAdjacentFreePlant(wizard);
        if (adjacent != null) {
            return adjacent;
        }
        return tickCounter >= castInterval() ? findFrontmostFreePlant(wizard) : null;
    }

    // Longer after the first one, as the game does: the opening hex arrives while the player still has
    // few plants down, and spacing every later one the same way would be relentless.
    private boolean castOnce;

    private int castInterval() {
        return castOnce ? SPELL_COOLDOWN : FIRST_SPELL_TICKS;
    }

    private void beginCast(Zombie wizard, Plant target) {
        tickCounter = 0;
        castTicks = 0;
        castOnce = true;
        aimedAt = target;
        // What the view listens for to play `sheep`. The wizard's OWN tile: nothing has been cast yet,
        // and what this sentence has to find is the zombie about to raise its staff.
        wizard.getGameSession().reportEvent(wizard.getAlias() + " raises its staff at ("
                + (int) wizard.getMovement().getPositionX() + ", "
                + wizard.getMovement().getPositionY() + ").");
    }

    private void advanceCast(Zombie wizard) {
        castTicks++;
        if (castTicks < CAST_TICKS) {
            return;
        }
        Plant target = aimedAt;
        castTicks = NOT_CASTING;
        aimedAt = null;
        // Re-checked on landing: two seconds is long enough for the plant to be eaten, dug up, or
        // hexed by another wizard. A spell that finds nothing simply fizzles.
        if (target == null || !canHex(target)) {
            return;
        }
        target.turnIntoSheep(wizard);
    }

    private Plant getAdjacentFreePlant(Zombie wizard) {
        if (wizard.getGameSession() == null || wizard.getGameSession().getMap() == null) {
            return null;
        }

        int rowIdx = wizard.getMovement().getPositionY();
        double zX = wizard.getMovement().getPositionX();
        Row row = wizard.getGameSession().getMap().getRow(rowIdx);
        if (row == null || row.getCells() == null) {
            return null;
        }

        for (Cell cell : row.getCells()) {
            if (cell != null && cell.hasPlant()
                    && Math.abs(zX - cell.getX()) <= REACH_TILES
                    && canHex(cell.getCurrentPlant())) {
                return cell.getCurrentPlant();
            }
        }
        return null;
    }

    private Plant findFrontmostFreePlant(Zombie wizard) {
        if (wizard.getGameSession() == null || wizard.getGameSession().getMap() == null) {
            return null;
        }

        int rowIdx = wizard.getMovement().getPositionY();
        Row row = wizard.getGameSession().getMap().getRow(rowIdx);
        if (row == null || row.getCells() == null) {
            return null;
        }

        for (int col = utils.Constants.BOARD_COLS - 1; col >= 0; col--) {
            Cell cell = row.cellAt(col);
            if (cell != null && cell.hasPlant() && canHex(cell.getCurrentPlant())) {
                return cell.getCurrentPlant();
            }
        }
        return null;
    }

    // Whether a plant can be turned into a sheep at all.
    //
    // Already-disabled plants are skipped because there is nothing left to take away -- a frozen or
    // octopus-wrapped plant is doing nothing already, and stacking a second spell on it would only make
    // it harder to get back. The plant-food exemption is the game's own: a boosted plant shrugs the hex
    // off, which is what makes feeding one a genuine answer to a wizard rather than wasted sun.
    private boolean canHex(Plant plant) {
        return plant != null && !plant.isDead() && !plant.isSheep()
                && !plant.isFrozen() && !plant.hasOctopus() && !plant.hasPlantFood();
    }
}
