package server.match;

import models.entities.collectibles.Sun;
import models.entities.plants.Plant;
import models.entities.projectiles.Projectile;
import models.entities.zombies.Components.ActionState;
import models.entities.zombies.Components.ArmorType;
import models.entities.zombies.Components.HealthLayer;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import models.game.gamemodes.BrainLawn;
import models.game.gamemodes.VersusIZombieMode;
import models.map.Cell;
import models.map.Lawnmower;
import models.map.Row;
import net.dto.EntityFlags;
import net.dto.EntityKind;
import net.dto.EntityState;
import net.packets.MatchSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// Turns the authoritative board into the packet the two clients draw from.
//
// The walk is deliberately the same one EntityInterpolator does on the client (rows -> zombies ->
// projectiles -> mower, then the session's suns, which live on the session and not on the map). That
// is not a coincidence to preserve by hand: if a collection is missed here it is invisible in the
// match, and the client's own smoothing pass is the existing, tested list of everywhere an entity can
// hide.
//
// ## What is NOT sent, and why that is fine
//
// Cooldowns, ability timers, wind-up state, chill counters, terrain. All of it is server-side rules
// that only ever produce a POSITION, a HEALTH or a FLAG -- which are sent. A client that knew a
// Peashooter's shot timer could draw a fractionally better wind-up animation; a client that guessed
// one would draw a shot that never happened. The rule this whole design rests on is that the client
// renders and never simulates.
//
// Single-threaded by contract: called from the match's tick thread, between two ticks, so nothing is
// mutating the board while it is being read.
final class SnapshotBuilder {

    private final NetIdRegistry ids = new NetIdRegistry();

    MatchSnapshot build(GameSession session) {
        List<EntityState> entities = new ArrayList<>();
        Set<Object> alive = NetIdRegistry.newIdentitySet();
        BrainLawn lawn = session.getMode() instanceof BrainLawn brainLawn ? brainLawn : null;

        for (Row row : session.getMap().getRows()) {
            for (Cell cell : row.getCells()) {
                addPlant(entities, alive, cell.getCurrentPlant());
                addPlant(entities, alive, cell.getProtector());
                addPlant(entities, alive, cell.getPlatform());
            }
            for (Zombie zombie : row.getZombies()) {
                add(entities, alive, zombie, zombieState(zombie, lawn));
            }
            for (Projectile projectile : row.getActiveProjectiles()) {
                add(entities, alive, projectile, projectileState(projectile));
            }
            Lawnmower mower = row.getLawnmower();
            if (mower != null) {
                add(entities, alive, mower, mowerState(mower, row));
            }
        }
        for (Sun sun : session.getActiveSuns()) {
            add(entities, alive, sun, sunState(sun));
        }

        ids.retainOnly(alive);

        VersusIZombieMode versus = session.getMode() instanceof VersusIZombieMode v ? v : null;
        return new MatchSnapshot(
                session.getTimeTicks(),
                versus == null ? 0 : versus.ticksRemaining(session),
                session.getSunAmount(),
                versus == null ? 0 : versus.getZombieSun(),
                session.getPlantFoodCount(),
                versus == null ? new boolean[0] : versus.brainState(),
                entities);
    }

    int trackedEntities() {
        return ids.size();
    }

    // ---- per-kind ----------------------------------------------------------------------------

    private void addPlant(List<EntityState> out, Set<Object> alive, Plant plant) {
        if (plant == null) {
            return;
        }
        // A cell can answer with the same plant twice -- getDefendingPlant falls back through
        // protector, platform and occupant -- and a duplicate netId in one snapshot makes the client's
        // reconciler create and immediately destroy the same mirror entity every tick.
        if (alive.contains(plant)) {
            return;
        }
        add(out, alive, plant, plantState(plant));
    }

    private void add(List<EntityState> out, Set<Object> alive, Object entity,
                     StateFields fields) {
        alive.add(entity);
        out.add(new EntityState(ids.idOf(entity), fields.kind, fields.type,
                fields.x, fields.y, fields.hp, fields.maxHp, fields.flags));
    }

    // A tiny carrier so each per-kind method reads as a list of facts rather than as eight positional
    // arguments to a constructor whose order nothing checks.
    private record StateFields(EntityKind kind, String type, float x, float y,
                               int hp, int maxHp, int flags) { }

    private StateFields plantState(Plant plant) {
        int flags = EntityFlags.NONE;
        flags = EntityFlags.with(flags, EntityFlags.FROZEN, plant.isFrozen());
        flags = EntityFlags.with(flags, EntityFlags.BOOSTED, plant.isPlantFoodActive());
        flags = EntityFlags.with(flags, EntityFlags.DYING, plant.isDead());
        return new StateFields(EntityKind.PLANT, plant.getName(),
                (float) plant.getX(), plant.getY(),
                plant.getHealth().getCurrentHp(), plant.getHealth().getMaxHp(), flags);
    }

    private StateFields zombieState(Zombie zombie, BrainLawn lawn) {
        int flags = EntityFlags.NONE;
        flags = EntityFlags.with(flags, EntityFlags.FROZEN, zombie.getState().isFrozen());
        flags = EntityFlags.with(flags, EntityFlags.EATING,
                zombie.getState().getCurrentAction() == ActionState.EATING);
        flags = EntityFlags.with(flags, EntityFlags.DYING, zombie.getHealth().isDead());
        flags = EntityFlags.with(flags, EntityFlags.SUN_PRODUCER,
                lawn != null && lawn.isSunProducer(zombie));
        flags |= armourFlags(zombie);
        // getPositionX/Y off the movement component, not getX()/getY(): a lane switch is in progress
        // between two whole rows, and the y an entity is FILED under is the rounded one.
        return new StateFields(EntityKind.ZOMBIE, zombie.getAlias(),
                (float) zombie.getMovement().getPositionX(), zombie.getMovement().getPositionY(),
                zombie.getHealth().getTotalHP(), zombie.getHealth().getMaxTotalHp(), flags);
    }

    // Which hats are still on. Three separate bits rather than a count, because Conehead, Buckethead
    // and Brick are ONE animation with three hideable parts -- the client switches them on
    // individually (ArmorVisibility), so it needs to know WHICH, not how many.
    //
    // Armour that lives on its own zombie's animation (a barrel, a newspaper, an ice block) is not
    // mapped: there is no shared part to hide, so the client draws it from the alias as it always did.
    private int armourFlags(Zombie zombie) {
        int flags = EntityFlags.NONE;
        for (HealthLayer layer : zombie.getHealth().getLayers()) {
            ArmorType type = layer.getType();
            if (type == ArmorType.CONE) {
                flags |= EntityFlags.ARMOUR_1;
            } else if (type == ArmorType.BUCKET) {
                flags |= EntityFlags.ARMOUR_2;
            } else if (type == ArmorType.BRICK) {
                flags |= EntityFlags.ARMOUR_3;
            }
        }
        return flags;
    }

    private StateFields projectileState(Projectile projectile) {
        // getExactY, not getY. A diagonal shot moves half a lane per tick, and the rounded row would
        // leave nothing between the lanes for the client to interpolate -- the failure
        // EntityInterpolator documents, arriving over the wire this time.
        return new StateFields(EntityKind.PROJECTILE, projectile.getType().name(),
                (float) projectile.getX(), (float) projectile.getExactY(),
                0, 0, EntityFlags.NONE);
    }

    private StateFields mowerState(Lawnmower mower, Row row) {
        return new StateFields(EntityKind.MOWER, "LAWNMOWER",
                (float) mower.getPositionX(), row.getIndex(), 0, 0, EntityFlags.NONE);
    }

    // A sun's `hp` is what it is WORTH. The field is otherwise unused for a collectible, and the
    // client's HUD wants to show the number; inventing a second int on EntityState for it would cost
    // every entity in every snapshot four bytes to carry a value only suns have.
    private StateFields sunState(Sun sun) {
        int flags = EntityFlags.with(EntityFlags.NONE, EntityFlags.FALLING, sun.isFalling());
        return new StateFields(EntityKind.SUN, sun.getType().name(),
                (float) sun.getX(), (float) sun.getCurrentY(),
                sun.getAmount(), sun.getAmount(), flags);
    }
}
