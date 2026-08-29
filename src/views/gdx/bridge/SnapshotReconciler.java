package views.gdx.bridge;

import factories.PlantFactory;
import factories.ZombieFactory;
import models.entities.collectibles.Sun;
import models.entities.collectibles.SunType;
import models.entities.plants.Plant;
import models.entities.projectiles.Element;
import models.entities.projectiles.Projectile;
import models.entities.projectiles.ProjectileType;
import models.entities.projectiles.Trajectory;
import models.entities.zombies.Components.ActionState;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import models.game.SeedPacket;
import models.game.gamemodes.VersusIZombieMode;
import models.map.Cell;
import models.map.Row;
import net.dto.EntityFlags;
import net.dto.EntityState;
import net.packets.MatchSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Makes a local board look exactly like the server's.
//
// The client keeps a real GameSession -- real Plants, real Zombies, real Rows -- and never ticks it.
// That is the whole trick behind the networked match: all 28 renderers in views/gdx/render keep
// reading the model types they have always read, so there is no second rendering path to write and no
// second one to keep in step. This class is the only thing that writes to that board.
//
// ## Why entities are rebuilt through the factories
//
// A snapshot carries a type name ("Peashooter", "ZombieGargantuar") because that is what the factories
// already take. Going through them means a mirrored zombie has its real armour stack, its real
// animation alias and its real category, so ArmorVisibility and ZombieRenderer work on it unchanged --
// which they would not on a hand-rolled shell built to satisfy a getter.
//
// ## What is written, and what is deliberately not
//
// Positions, membership, health and the flags the views draw from. Nothing else: no abilities are
// armed, no cooldowns run, no ability timer is guessed at. The mirror's systems never execute, so
// anything not written here simply sits at its constructed value -- which is correct, because the
// server is the only thing entitled to an opinion about it.
//
// ## Order matters
//
// Removals happen BEFORE creations. A plant dug up and another planted on the same tile in the same
// tick arrive in one snapshot, and creating first would hit an occupied cell, be refused, and leave
// the board one plant short for the rest of the match.
public final class SnapshotReconciler {

    private final GameSession session;
    private final Map<Integer, Object> byNetId = new HashMap<>();

    private long lastTick = -1;

    public SnapshotReconciler(GameSession session) {
        this.session = session;
    }

    public void apply(MatchSnapshot snapshot) {
        if (snapshot == null || session == null) {
            return;
        }
        // Out-of-order delivery cannot happen over TCP, but a snapshot can be applied twice if a
        // caller replays one. Applying an older board over a newer one would rubber-band everything.
        if (snapshot.tick() < lastTick) {
            return;
        }
        lastTick = snapshot.tick();

        applyBanks(snapshot);
        applySeedCooldowns(snapshot);
        removeMissing(collectIds(snapshot));
        for (EntityState state : snapshot.entities()) {
            applyEntity(state);
        }
    }

    public int mirroredCount() {
        return byNetId.size();
    }

    public long lastTick() {
        return lastTick;
    }

    // ---- the numbers above the board -------------------------------------------------------------

    private void applyBanks(MatchSnapshot snapshot) {
        // Through the session's own arithmetic rather than a setter, because there is no setter and
        // adding one would be a field the rest of the game could write to for no reason.
        session.increaseSunAmount(snapshot.sunPlants() - session.getSunAmount());
        int foodDelta = snapshot.plantFood() - session.getPlantFoodCount();
        if (foodDelta > 0) {
            session.increasePlantFoodCount(foodDelta);
        } else if (foodDelta < 0) {
            session.decreasePlantFoodCount(-foodDelta);
        }
        if (session.getMode() instanceof VersusIZombieMode versus) {
            versus.mirror(snapshot.ticksRemaining(), snapshot.sunZombies(), snapshot.brainEaten());
        }
    }

    // Every packet is written every tick, not just the ones in the map: a packet that has finished
    // recharging simply stops being listed, and one that was never told zero would stay dark forever.
    private void applySeedCooldowns(MatchSnapshot snapshot) {
        Map<String, Integer> cooling = snapshot.cooldowns();
        for (SeedPacket packet : session.getSelectedSeeds()) {
            packet.mirrorRemainingTicks(remainingFor(cooling, packet.getPlantType()));
        }
    }

    // Ignoring case, which is the house rule for plant names everywhere else in this project -- a
    // "Wall-nut" that arrives as "wall-nut" would silently never recharge rather than fail loudly.
    private static int remainingFor(Map<String, Integer> cooling, String plantType) {
        for (Map.Entry<String, Integer> entry : cooling.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(plantType)) {
                return entry.getValue() == null ? 0 : entry.getValue();
            }
        }
        return 0;
    }

    // ---- membership ------------------------------------------------------------------------------

    private Set<Integer> collectIds(MatchSnapshot snapshot) {
        Set<Integer> ids = new HashSet<>();
        for (EntityState state : snapshot.entities()) {
            ids.add(state.netId());
        }
        return ids;
    }

    private void removeMissing(Set<Integer> stillThere) {
        List<Integer> gone = new ArrayList<>();
        for (Integer id : byNetId.keySet()) {
            if (!stillThere.contains(id)) {
                gone.add(id);
            }
        }
        for (Integer id : gone) {
            remove(byNetId.remove(id));
        }
    }

    private void remove(Object entity) {
        if (entity instanceof Zombie zombie) {
            for (Row row : session.getMap().getRows()) {
                row.getZombies().removeIf(other -> other == zombie);
            }
            if (session.getMode() instanceof VersusIZombieMode versus) {
                versus.forgetSunProducer(zombie);
            }
        } else if (entity instanceof Plant plant) {
            removePlant(plant);
        } else if (entity instanceof Projectile projectile) {
            for (Row row : session.getMap().getRows()) {
                row.getActiveProjectiles().removeIf(other -> other == projectile);
            }
        } else if (entity instanceof Sun sun) {
            session.getActiveSuns().removeIf(other -> other == sun);
        }
    }

    // Unlinked by identity rather than through Cell.removePlant(), which removes whatever is currently
    // in the cell -- and by the time a death reaches us the cell may already hold something else.
    private void removePlant(Plant plant) {
        for (Row row : session.getMap().getRows()) {
            for (Cell cell : row.getCells()) {
                if (cell.getCurrentPlant() == plant || cell.getProtector() == plant
                        || cell.getPlatform() == plant) {
                    cell.removePlant();
                    return;
                }
            }
        }
    }

    // ---- per entity ------------------------------------------------------------------------------

    private void applyEntity(EntityState state) {
        Object existing = byNetId.get(state.netId());
        switch (state.kind()) {
            case ZOMBIE -> applyZombie(state, existing instanceof Zombie z ? z : createZombie(state));
            case PLANT -> applyPlant(state, existing instanceof Plant p ? p : createPlant(state));
            case PROJECTILE -> applyProjectile(state,
                    existing instanceof Projectile p ? p : createProjectile(state));
            case SUN -> applySun(state, existing instanceof Sun s ? s : createSun(state));
            // A mower is the one thing on the board the server does not currently send -- a versus
            // lawn has brains in those slots instead. Handled by clearMowers(), which is what a
            // mirrored brain lawn actually needs: the mirror's GameMap builds mowers in its
            // constructor and the server's board has none.
            case MOWER, VASE -> { }
            default -> { }
        }
    }

    // ---- zombies ---------------------------------------------------------------------------------

    private Zombie createZombie(EntityState state) {
        Zombie zombie = ZombieFactory.createZombie(state.type(), state.x(),
                Math.round(state.y()), session);
        if (zombie == null) {
            return null;
        }
        // Speed zero: nothing on this board moves under its own power, and a mirrored zombie whose
        // movement component still had a speed would be a second opinion waiting for something to
        // call move() on it.
        zombie.getMovement().setSpeed(0);
        session.getMap().getRow(clampLane(state.y())).getZombies().add(zombie);
        byNetId.put(state.netId(), zombie);
        if (state.is(EntityFlags.SUN_PRODUCER)
                && session.getMode() instanceof VersusIZombieMode versus) {
            versus.markSunProducer(zombie);
        }
        return zombie;
    }

    private void applyZombie(EntityState state, Zombie zombie) {
        if (zombie == null) {
            return;
        }
        int lane = clampLane(state.y());
        if (zombie.getMovement().getPositionY() != lane) {
            refile(zombie, lane);
        }
        zombie.getMovement().setPositionX(state.x());
        zombie.getState().setFrozen(state.is(EntityFlags.FROZEN));
        zombie.getState().setAction(state.is(EntityFlags.DYING) ? ActionState.DYING
                : state.is(EntityFlags.EATING) ? ActionState.EATING : ActionState.WALKING);
        syncZombieHealth(state, zombie);
    }

    // The lane lives in TWO places -- the movement component and the Row's list -- and only both
    // together are the zombie's position. Setting one is the bug this project has already had once.
    private void refile(Zombie zombie, int lane) {
        for (Row row : session.getMap().getRows()) {
            row.getZombies().removeIf(other -> other == zombie);
        }
        zombie.getMovement().setPositionY(lane);
        session.getMap().getRow(lane).getZombies().add(zombie);
    }

    // Applied as DAMAGE rather than written into the layer stack, so the real machinery runs: armour
    // peels off in the right order, isDead() flips at the right moment, and the view's damage flash
    // and armour-loss animations fire on a mirrored zombie exactly as on a simulated one.
    //
    // Healing is not mirrored. Nothing in this mode heals a zombie, and driving hp upwards would mean
    // inventing which layer got it back.
    private void syncZombieHealth(EntityState state, Zombie zombie) {
        int current = zombie.getHealth().getTotalHP();
        if (current > state.hp()) {
            zombie.getHealth().applyDamage(current - state.hp(), null, null);
        }
    }

    // ---- plants ----------------------------------------------------------------------------------

    private Plant createPlant(EntityState state) {
        int x = columnOf(state.x());
        int y = clampLane(state.y());
        if (!session.getMap().isValidCoordinate(x, y)) {
            return null;
        }
        // Level 1 always. Plant levels are the plant player's own upgrades and the server deliberately
        // plays the match on a neutral profile, so anything else here would draw a card the server is
        // not simulating.
        Plant plant = PlantFactory.createPlant(state.type(), 1, x, y);
        if (plant == null) {
            return null;
        }
        session.getMap().getCell(x, y).addPlant(plant);
        byNetId.put(state.netId(), plant);
        return plant;
    }

    private void applyPlant(EntityState state, Plant plant) {
        if (plant == null) {
            return;
        }
        plant.setFrozen(state.is(EntityFlags.FROZEN));
        plant.mirrorWindingUp(state.is(EntityFlags.ACTING));
        plant.mirrorPlantFood(state.is(EntityFlags.BOOSTED));
        int current = plant.getHealth().getCurrentHp();
        if (current > state.hp()) {
            plant.getHealth().takeDamage(current - state.hp());
        } else if (current < state.hp()) {
            plant.getHealth().heal(state.hp() - current);
        }
    }

    // ---- projectiles and suns --------------------------------------------------------------------

    private Projectile createProjectile(EntityState state) {
        ProjectileType type = projectileType(state.type());
        if (type == null) {
            return null;
        }
        // Zero damage, zero speed, no shooter: this shot will never be updated, never collide and
        // never be asked who fired it. It exists to be drawn.
        Projectile projectile = new Projectile(state.x(), state.y(), type, 0, 0, 0, null, 0,
                Element.NEUTRAL, Trajectory.DIRECT);
        session.getMap().getRow(clampLane(state.y())).addProjectile(projectile);
        byNetId.put(state.netId(), projectile);
        return projectile;
    }

    private void applyProjectile(EntityState state, Projectile projectile) {
        if (projectile == null) {
            return;
        }
        int lane = clampLane(state.y());
        if (projectile.getY() != lane) {
            for (Row row : session.getMap().getRows()) {
                row.getActiveProjectiles().removeIf(other -> other == projectile);
            }
            session.getMap().getRow(lane).addProjectile(projectile);
        }
        projectile.placeAt(state.x(), state.y());
    }

    // hp carries the sun's WORTH and maxHp the height it comes to rest at -- see EntityState. The rest
    // height must come from the snapshot rather than from wherever the sun happens to be right now: a
    // sun caught mid-fall is collected by naming the tile it is falling TOWARDS, and one built with
    // targetY = its current height addresses a row the server does not have it in, so clicking it does
    // nothing for the whole of the fall.
    //
    // The expiry is a large number rather than the server's: a mirrored sun is removed when it stops
    // appearing in snapshots, and letting it expire locally would make it vanish while the server still
    // has it.
    private Sun createSun(EntityState state) {
        SunType type = sunType(state.type());
        boolean falling = state.is(EntityFlags.FALLING);
        Sun sun = new Sun(state.x(), state.y(), state.restHeight(), type, state.hp(), falling,
                Integer.MAX_VALUE);
        session.addSun(sun);
        byNetId.put(state.netId(), sun);
        return sun;
    }

    private void applySun(EntityState state, Sun sun) {
        if (sun == null) {
            return;
        }
        sun.placeAt(state.x(), state.y(), state.restHeight(), state.is(EntityFlags.FALLING));
    }

    // ---- setup -----------------------------------------------------------------------------------

    // The mirror's GameMap builds a lawnmower in every row, and a brain lawn has none -- the server
    // removed them in onStart, on a board this client never ran. Called once when the match starts.
    public void clearMowers() {
        for (Row row : session.getMap().getRows()) {
            row.setLawnmower(null);
        }
    }

    // ---- helpers ---------------------------------------------------------------------------------

    // Which column a plant's x belongs to.
    //
    // Floor, NOT round -- and this is the one number in this file that is easy to get wrong and hard
    // to see wrong. PlantFactory places a plant at `column + 0.5`, the middle of its tile, so a plant
    // in column 2 has x = 2.5 and rounding it lands it in column 3. The mirrored board then draws
    // every plant one tile to the right of where the server has it, and the plant the plant player
    // just paid for appears somewhere they did not click.
    private static int columnOf(float x) {
        return (int) Math.floor(x);
    }

    private int clampLane(float y) {
        int lane = Math.round(y);
        int rows = session.getMap().getRows().size();
        return Math.max(0, Math.min(rows - 1, lane));
    }

    private static ProjectileType projectileType(String name) {
        try {
            return ProjectileType.valueOf(name);
        } catch (IllegalArgumentException | NullPointerException unknown) {
            return null;
        }
    }

    private static SunType sunType(String name) {
        try {
            return SunType.valueOf(name);
        } catch (IllegalArgumentException | NullPointerException unknown) {
            return SunType.NORMAL;
        }
    }
}
