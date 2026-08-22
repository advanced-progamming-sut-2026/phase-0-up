package models.entities.zombies.Abilities;

import models.entities.zombies.Zombie;

public class IceImmunityAbility implements ZombieAbility {

    @Override
    public void execute(Zombie zombie) {
        if (zombie == null || zombie.getState() == null) {
            return;
        }

        // Only the TIMED freeze an ice attack leaves. Being immune to a Snow Pea is not the same thing
        // as being able to walk out of a block of ice you were authored inside: a level that entombs a
        // zombie via FrozenTerrain uses setFrozen (permanent), and clearing that here let level 2-2's
        // pre-frozen Ice Age Hunter shrug off its own block on the first tick and stroll up the lawn
        // before the player had planted anything.
        //
        // StateComponent already draws this line -- applyFreeze refuses for an immune zombie while
        // setFrozen does not -- so in practice a timed freeze can only be here if the immunity was
        // granted after the hit landed. Clearing it is still right; clearing the block is not.
        if (zombie.getState().getFrozenTimer() > 0 && !zombie.getState().isPermanentlyFrozen()) {
            zombie.getState().setFrozenTimer(0);
            zombie.getGameSession().reportEvent(zombie.getAlias() + " shrugs off the freeze at ("
                    + (int) zombie.getX() + ", " + zombie.getY() + ").");
        }

        if (zombie.getState().isChilled()) {
            zombie.getState().setChilledTimer(0);
            zombie.getGameSession().reportEvent(zombie.getAlias() + " ignores the chill at ("
                    + (int) zombie.getX() + ", " + zombie.getY() + ").");
        }
    }

    public boolean isImmuneToIce() {
        return true;
    }
}