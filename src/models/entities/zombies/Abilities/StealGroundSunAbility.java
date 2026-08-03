package models.entities.zombies.Abilities;

import models.entities.collectibles.Sun;
import models.entities.zombies.Zombie;
import models.game.GameSession;

import java.util.ArrayList;

// Ra Zombie: "it drags the suns that are lying on the ground toward itself and steals them. After it is
// killed, ALL the stolen sun returns to the player." (documents/project.md line 1221.)
//
// This is a different theft from the Turquoise's. The Turquoise drains the sun the player has already
// BANKED (StealSunAbility); Ra never touches the bank -- it robs the lawn, taking the sun tokens the
// player has not picked up yet. Conflating the two let Ra drain a bank the spec never gives it access
// to, and let it steal on a lawn with no sun lying on it at all.
public class StealGroundSunAbility implements ZombieAbility {
    // How far Ra's pull reaches, in tiles.
    private final double pullRadius;
    // How much sun it can carry, from the blueprint's MaxClaimedSunCurrency.
    private final int maxStolenSun;
    // Tiles a caught sun slides toward Ra each tick, and how close it must get to be pocketed.
    private static final double PULL_SPEED = 0.15;
    private static final double GRAB_DISTANCE = 0.35;

    private int totalStolenSun = 0;

    public StealGroundSunAbility(double pullRadius, int maxStolenSun) {
        this.pullRadius = pullRadius;
        this.maxStolenSun = maxStolenSun > 0 ? maxStolenSun : Integer.MAX_VALUE;
    }

    @Override
    public void execute(Zombie zombie) {
        if (zombie == null || zombie.getGameSession() == null || zombie.getMovement() == null) {
            return;
        }
        if (zombie.getState().isFrozen() || zombie.getState().isButtered()) {
            return;   // a zombie held in place cannot reel anything in
        }
        if (totalStolenSun >= maxStolenSun) {
            return;   // arms full
        }

        GameSession session = zombie.getGameSession();
        double zombieX = zombie.getMovement().getPositionX();
        int zombieRow = zombie.getMovement().getPositionY();

        // A copy: pocketing a sun marks it removable and SunSystem may drop it from the live list.
        for (Sun sun : new ArrayList<>(session.getActiveSuns())) {
            if (sun.isRemovable()) {
                continue;
            }
            double dx = zombieX - sun.getX();
            double dy = zombieRow - sun.getY();
            if (Math.sqrt((dx * dx) + (dy * dy)) > pullRadius) {
                continue;
            }
            if (Math.abs(dx) <= GRAB_DISTANCE && sun.getY() == zombieRow) {
                pocket(zombie, session, sun);
                if (totalStolenSun >= maxStolenSun) {
                    return;
                }
            } else {
                haulIn(sun, dx, dy);
            }
        }
    }

    // Slides one sun a step toward Ra, closing the lane gap first so it ends up in reach.
    private void haulIn(Sun sun, double dx, double dy) {
        if (Math.abs(dx) > GRAB_DISTANCE) {
            double step = Math.min(PULL_SPEED, Math.abs(dx));
            sun.setX(sun.getX() + (dx > 0 ? step : -step));
        }
        if (dy != 0 && Math.abs(dx) <= 1.0) {
            sun.setY(sun.getY() + (dy > 0 ? 1 : -1));
        }
    }

    // Takes the sun off the board without paying the player for it, capped by what Ra can still carry.
    private void pocket(Zombie zombie, GameSession session, Sun sun) {
        int room = maxStolenSun - totalStolenSun;
        int taken = Math.min(sun.getAmount(), room);
        sun.steal();
        totalStolenSun += taken;
        session.reportEvent("The " + zombie.getAlias() + " drags a sun off the lawn and pockets it ("
                + totalStolenSun + " sun stolen so far).");
    }

    // The whole haul comes back when Ra dies -- the spec says ALL of it, unlike the Turquoise's half.
    public int getSunDropAmountOnDeath() {
        return totalStolenSun;
    }

    public int getTotalStolenSun() {
        return totalStolenSun;
    }
}
