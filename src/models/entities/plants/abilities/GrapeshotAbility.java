package models.entities.plants.abilities;

import models.entities.plants.Plant;
import models.entities.projectiles.Element;
import models.entities.projectiles.Projectile;
import models.entities.projectiles.ProjectileType;
import models.entities.projectiles.Trajectory;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import utils.Constants;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Grapeshot: a 3x3 blast (inherited) PLUS a scatter of bouncing grape pellets. The blast is what
// InstantExplosiveAbility already does; this subclass hangs the pellets off the same detonation.
//
// Each pellet ricochets from zombie to zombie, dealing GRAPESHOT_GRAPE_DAMAGE per hit and striking
// (1 + bounces) distinct zombies before it is spent. The "Bounces +1" upgrade (GRAPE_BOUNCE_EXT)
// raises `bounces` -- which is why that upgrade lives here now, instead of quietly widening the blast.
public class GrapeshotAbility extends InstantExplosiveAbility {
    private int bounces;

    public GrapeshotAbility(int damage, int explosionRowRadius, int explosionColRadius, Element element,
                            int baseBounces) {
        super(damage, explosionRowRadius, explosionColRadius, element);
        this.bounces = baseBounces;
    }

    // Upgrade (GRAPE_BOUNCE_EXT, "Bounces +1"): every pellet now ricochets to one more zombie.
    public void addBounces(int extra) {
        this.bounces += extra;
    }

    // Runs on the same detonation as the blast: the parent handles the 3x3 damage and consumes the
    // plant, then the pellets are launched from where the plant stood.
    @Override
    protected void detonate(Plant owner, GameSession gameSession) {
        super.detonate(owner, gameSession);
        launchGrapes(owner, gameSession);
    }

    // Fires one pellet at each of the nearest zombies (up to a cap), so the volley fans out across the
    // cluster rather than every grape chasing the same target. With no zombie on the board there is
    // nothing to shoot at, so the scatter is skipped entirely.
    private void launchGrapes(Plant owner, GameSession gameSession) {
        List<Zombie> targets = nearestZombies(owner, gameSession, Constants.GRAPESHOT_MAX_GRAPES);
        if (targets.isEmpty()) {
            return;
        }
        int ttl = Constants.GRAPESHOT_GRAPE_LIFESPAN_SECONDS * Constants.TICKS_PER_SECOND;
        // One shared "already struck" set for the whole volley, so the pellets spread across the crowd
        // instead of several converging on the same nearest zombie.
        Set<Zombie> volleyHits = new HashSet<>();
        for (Zombie firstTarget : targets) {
            Projectile grape = new Projectile(owner.getX(), owner.getY(), ProjectileType.GRAPE,
                    Constants.GRAPESHOT_GRAPE_DAMAGE, 0, 0, owner, 0.0, element, Trajectory.DIRECT);
            grape.makeGrape(bounces, ttl, firstTarget, volleyHits);
            gameSession.getMap().getRow(firstTarget.getMovement().getPositionY()).addProjectile(grape);
        }
        gameSession.reportEvent("Grapeshot bursts into a spray of bouncing grapes at ("
                + (int) owner.getX() + ", " + owner.getY() + ")!");
    }

    // The `limit` targetable zombies closest to the plant, nearest first. Distinct first-hop targets so
    // the pellets spread; each pellet then bounces onward under its own steam.
    private List<Zombie> nearestZombies(Plant owner, GameSession gameSession, int limit) {
        List<Zombie> zombies = new ArrayList<>();
        for (int row = 0; row < Constants.BOARD_ROWS; row++) {
            List<Zombie> inRow = gameSession.getMap().getRow(row).getZombies();
            if (inRow == null) {
                continue;
            }
            for (Zombie z : inRow) {
                if (z.isTargetable()) {
                    zombies.add(z);
                }
            }
        }
        zombies.sort(Comparator.comparingDouble(z -> distanceSquared(owner, z)));
        return zombies.size() > limit ? new ArrayList<>(zombies.subList(0, limit)) : zombies;
    }

    private double distanceSquared(Plant owner, Zombie z) {
        double dx = z.getMovement().getPositionX() - owner.getX();
        double dy = z.getMovement().getPositionY() - owner.getY();
        return (dx * dx) + (dy * dy);
    }
}
