package models.entities.plants.abilities;

import models.entities.plants.Plant;
import models.entities.plants.abilities.triggers.TriggerStrategy;
import models.entities.projectiles.Element;
import models.game.GameSession;

// Tangle Kelp: wraps around whatever steps onto its tile and hauls it under the water.
//
// It was built as a DelayedExplosiveAbility with a zero blast radius, which is arithmetically the same
// thing -- area damage over a one-tile area kills exactly the zombie standing on the kelp -- and
// completely wrong on screen. Everything downstream keys off the sentence AreaExplosiveAbility.detonate
// emits, so the kelp set off a Cherry Bomb fireball underwater and turned its catch to ash. A kelp does
// not blow up; it pulls something down and goes with it.
//
// Still an AreaExplosiveAbility, because the area damage IS how it catches things and because the
// "Grabs +1 zombie" upgrade widens exactly that (UpgradeResolver's BONUS_GRAB_TARGETS).
public class TangleKelpAbility extends AreaExplosiveAbility {

    // How long the kelp spends dragging. Matched to the length of TANGLEKELP's `attack` clip so the
    // grab is over on the frame the zombie disappears rather than a moment either side of it.
    private static final int GRAB_TICKS = 25;

    private int grabRemaining = -1;

    public TangleKelpAbility(int actionInterval, TriggerStrategy triggerStrategy, int damage,
                             int rowRadius, int colRadius, Element element) {
        super(actionInterval, triggerStrategy, damage, rowRadius, colRadius, element);
    }

    // The grab is the plant's action, so the view plays `attack` through it. Without this the kelp
    // caught a zombie in a single tick and neither of them was ever seen doing anything.
    @Override
    public boolean isWindingUp() {
        return grabRemaining >= 0;
    }

    @Override
    public boolean canExecute(Plant owner, GameSession gameSession) {
        if (grabRemaining >= 0) {
            return false;   // already holding one
        }
        return super.canExecute(owner, gameSession);
    }

    @Override
    public void update(Plant owner, GameSession gameSession) {
        if (grabRemaining > 0) {
            grabRemaining--;
        } else if (grabRemaining == 0) {
            grabRemaining = -1;
            pullUnder(owner, gameSession);
        }
        super.update(owner, gameSession);
    }

    @Override
    public void execute(Plant owner, GameSession gameSession) {
        grabRemaining = GRAB_TICKS;
    }

    // Deliberately NOT detonate(): the damage is the same, the announcement is not. Anything that reads
    // "detonates" draws a blast, and this is a plant taking something down with it.
    private void pullUnder(Plant owner, GameSession gameSession) {
        AreaAttack.strike(gameSession, owner, explosionRowRadius, explosionColRadius, damage, element);

        gameSession.reportEvent("The Tangle Kelp hauls a zombie under at ("
                + (int) owner.getX() + ", " + owner.getY() + ") -- both gone in one gulp!");

        if (owner.getHealth() != null) {
            owner.getHealth().takeDamage(owner.getHealth().getMaxHp());
        }
    }
}
