package models.entities.zombies.Abilities;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.map.Cell;
import models.map.Row;

public class ThrowIceAbility implements ZombieAbility {
    private int tickCounter = 0;
    private static final int TICKS_PER_SECOND = 10;
    private static final int THROW_COOLDOWN = 4 * TICKS_PER_SECOND;

    // The Hunter TAKES AIM first, and the snowball lands when the throw finishes.
    //
    // The plant used to ice over on the same tick the cooldown expired, which put the effect before its
    // cause: the splat appeared, and only then did the zombie start swinging its arm. So the throw is
    // two moments -- the aim, and the hit -- with the animation running in between.
    //
    // 21 ticks is the length of ZOMBIE_ICEAGE_HUNTER's `throw` clip, 2.1 seconds at 10 Hz. The two are
    // the same duration by construction, as SummonGraveAbility's chant is: change one and the other has
    // to move with it, or the ice lands while the arm is still going back.
    private static final int AIM_TICKS = 21;

    private static final int NOT_AIMING = -1;
    private int aimTicks = NOT_AIMING;
    // Chosen when the aim starts, so the throw is pointed at something rather than acquiring a target
    // out of the air two seconds later. Re-checked on release -- a lot can happen to a plant in 2.1s.
    private Plant aimedAt;

    @Override
    public void execute(Zombie zombie) {
        if (zombie == null || zombie.getState().isUnableToMove()) {
            // Frozen or held: the arm stops and the snowball is dropped. The cooldown starts again.
            aimTicks = NOT_AIMING;
            aimedAt = null;
            return;
        }

        if (aimTicks != NOT_AIMING) {
            advanceAim(zombie);
            return;
        }

        tickCounter++;
        if (tickCounter >= THROW_COOLDOWN) {
            Plant target = findClosestUnfrozenPlant(zombie);

            if (target != null) {
                tickCounter = 0;
                aimTicks = 0;
                aimedAt = target;
                // What the view listens for to play `throw`. No ice yet -- that is the sentence below.
                zombie.getGameSession().reportEvent(zombie.getAlias() + " takes aim at " + target.getName()
                        + " at (" + (int) target.getX() + ", " + target.getY() + ").");
            }
        }
    }

    private void advanceAim(Zombie zombie) {
        aimTicks++;
        if (aimTicks < AIM_TICKS) {
            return;
        }
        Plant target = aimedAt;
        aimTicks = NOT_AIMING;
        aimedAt = null;
        // Someone else may have killed or frozen it while the arm was going back. A snowball thrown at
        // nothing simply misses: no ice, and no splat, because the sentence that draws one is not said.
        if (target == null || target.isDead() || target.isFrozen()) {
            return;
        }
        target.takeIceHit();
        zombie.getGameSession().reportEvent(zombie.getAlias() + " hurls ice at " + target.getName()
                + " at (" + (int) target.getX() + ", " + target.getY() + ").");
    }


    private Plant findClosestUnfrozenPlant(Zombie zombie) {
        if (zombie.getMovement() == null || zombie.getGameSession() == null
                || zombie.getGameSession().getMap() == null) {
            return null;
        }

        int rowIdx = zombie.getMovement().getPositionY();
        double zX = zombie.getMovement().getPositionX();

        Row row = zombie.getGameSession().getMap().getRow(rowIdx);
        if (row == null || row.getCells() == null) {
            return null;
        }

        Plant closestPlant = null;
        double minDistance = Double.MAX_VALUE;

        for (Cell cell : row.getCells()) {
            if (cell != null && cell.getCurrentPlant() != null) {
                Plant plant = cell.getCurrentPlant();
                if (!plant.isDead() && !plant.isFrozen()) {
                    double distance = zX - cell.getX();
                    if (distance >= -0.2 && distance < minDistance) {
                        minDistance = distance;
                        closestPlant = plant;
                    }
                }
            }
        }

        return closestPlant;
    }
}