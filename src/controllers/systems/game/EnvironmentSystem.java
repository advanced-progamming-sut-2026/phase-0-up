package controllers.systems.game;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.game.EnvironmentType;
import models.game.GameSession;
import models.game.Wave;
import models.map.Cell;
import models.map.GameMap;
import models.map.Row;
import models.map.Terrains.FrozenTerrain;
import models.map.Terrains.GraveInDarkAgesTerrain;
import models.map.Terrains.GravesInDarkAgesTypes;
import models.map.Terrains.SlipTerrain;
import models.map.Terrains.Terrain;
import utils.Constants;
import utils.Result;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// Home for season terrain and weather effects -- the ones that live on the board rather than on a
// single plant or zombie. Two entry points:
//   * tick(session)       -- per frame, after entities move (slider tiles, ice-block melting).
//   * onWaveStart(session,wave) -- when a wave launches (Frostbite freezing wind; later tides, graves).
//
// The engine drives tick(); the WaveSystem calls onWaveStart() as it launches each wave.
public class EnvironmentSystem {
    private final Random random;

    public EnvironmentSystem() {
        this(new Random());
    }

    // Seeded variant so the freezing wind (which rolls random rows) is reproducible in a test.
    public EnvironmentSystem(Random random) {
        this.random = random != null ? random : new Random();
    }

    // The season this level belongs to, derived from its authored chapter. Central so the WaveSystem
    // and this system agree on what "Frostbite Caves" means without each re-parsing the string.
    public static EnvironmentType environmentOf(GameSession session) {
        if (session == null || session.getLevel() == null || session.getLevel().getTemplate() == null) {
            return EnvironmentType.ANCIENT_EGYPT;
        }
        return EnvironmentType.fromChapter(session.getLevel().getTemplate().getChapter());
    }

    public void tick(GameSession session) {
        if (session == null) {
            return;
        }
        applySliderTiles(session);
        applyIceMelt(session);
    }

    // Wave-launch weather. Returns any lines to render.
    public List<Result> onWaveStart(GameSession session, Wave wave) {
        List<Result> events = new ArrayList<>();
        if (session == null || wave == null) {
            return events;
        }
        EnvironmentType season = environmentOf(session);
        if (season == EnvironmentType.FROSTBITE_CAVES) {
            applyFreezingWind(session, events);
        } else if (season == EnvironmentType.BIG_WAVE_BEACH) {
            applyTide(session, events);
        } else if (season == EnvironmentType.DARK_AGES) {
            applyDarkAgesGraves(session, events);
            applyNecromancy(session, events);
        }
        return events;
    }

    // Dark Ages: each wave, a handful of fresh graves rise on empty ground. Exactly half of them (the
    // count rounded down) hold loot -- a sun or a plant food, split evenly -- and the rest are empty.
    // The loot spills when the player breaks the grave open with shots.
    private void applyDarkAgesGraves(GameSession session, List<Result> events) {
        List<Cell> empty = new ArrayList<>();
        for (Row row : session.getMap().getRows()) {
            for (Cell cell : row.getCells()) {
                if (!cell.hasPlant() && !cell.hasProtector() && !cell.hasPlatform()
                        && !cell.isFlooded() && cell.isPlantable() && cell.getTerrain().isEmpty()) {
                    empty.add(cell);
                }
            }
        }
        if (empty.isEmpty()) {
            return;
        }
        java.util.Collections.shuffle(empty, random);
        // Spawn an even batch (2 or 4) so exactly half can bear loot -- an odd batch could not split
        // 50/50. Capped by the empty tiles available, kept even.
        int count = Math.min(2 * (1 + random.nextInt(2)), empty.size());
        if (count % 2 == 1) {
            count--;
        }
        if (count == 0) {
            return;
        }
        int lootCount = count / 2;                                    // exactly half hold loot

        for (int i = 0; i < count; i++) {
            GravesInDarkAgesTypes type;
            if (i < lootCount) {
                type = random.nextBoolean() ? GravesInDarkAgesTypes.SUNNY : GravesInDarkAgesTypes.FOODY;
            } else {
                type = GravesInDarkAgesTypes.PLAIN;
            }
            empty.get(i).addTerrain(new GraveInDarkAgesTerrain(type, session, empty.get(i)));
        }
        events.add(new Result(true, count + " graves rose from the ground; " + lootCount
                + " of them hold loot."));
    }

    // Necromancy tiles ('0') can raise a zombie straight out of the ground at the start of a wave,
    // like a low-sand ambush but on land. Rolled per tile.
    private void applyNecromancy(GameSession session, List<Result> events) {
        for (Row row : session.getMap().getRows()) {
            for (Cell cell : row.getCells()) {
                boolean necromancy = cell.getTerrain().stream()
                        .anyMatch(t -> t instanceof models.map.Terrains.NecromancyTerrain);
                if (!necromancy || random.nextDouble() >= Constants.LOW_TIDE_SURFACE_PROBABILITY) {
                    continue;
                }
                int col = (int) Math.floor(cell.getX());
                Zombie raised = factories.ZombieFactory.createZombie("ZombieDefault", col + 0.5,
                        row.getIndex(), session);
                if (raised != null) {
                    row.getZombies().add(raised);
                    events.add(new Result(true, "A zombie claws up from a necromancy grave at (" + col + ", "
                            + row.getIndex() + ")."));
                }
            }
        }
    }

    // Big Wave Beach tide. Each wave the waterline steps one column: it floods further left up to
    // TIDE_MAX_RISE columns past the resting line, then recedes back, oscillating. Flooding a tile
    // destroys a non-aquatic plant that has no Lily Pad under it, and a low-sand tile that goes under
    // water may let a zombie surface from beneath it.
    private void applyTide(GameSession session, List<Result> events) {
        GameMap map = session.getMap();
        if (!map.hasTide()) {
            return;
        }
        // Tell the player, once, exactly which columns the tide can never touch, so they know where it
        // is always safe to build.
        if (!map.isTideAnnounced()) {
            map.setTideAnnounced(true);
            int floor = map.getTideFloodFloor();
            events.add(new Result(true, "The tide can flood columns " + floor + " and rightward; columns 0 to "
                    + (floor - 1) + " are always safe from flooding."));
        }

        int base = map.getBaseWaterColumn();
        int level = map.getTideLevel();
        boolean rising = map.isTideRising();
        int maxLevel = base - map.getTideFloodFloor();   // how far the tide may rise past the waterline

        // A waterline already inside the safe zone leaves nothing to flood; the tide just idles.
        if (maxLevel <= 0) {
            surfaceLowTideZombies(session, events);
            return;
        }

        if (rising && level >= maxLevel) {
            rising = false;
        } else if (!rising && level <= 0) {
            rising = true;
        }

        if (rising) {
            floodColumn(session, base - level - 1, events);   // one column further left
            level++;
        } else {
            drainColumn(session, base - level, events);       // pull the leftmost extra column back
            level--;
        }
        map.setTideLevel(level);
        map.setTideRising(rising);

        surfaceLowTideZombies(session, events);
    }

    // Floods one column across every row: marks it underwater and destroys any non-aquatic plant that
    // is not floating on a Lily Pad. Aquatic plants and Lily Pads ride the tide.
    private void floodColumn(GameSession session, int column, List<Result> events) {
        if (column < 0) {
            return;
        }
        for (Row row : session.getMap().getRows()) {
            Cell cell = row.cellAt(column);
            if (cell.isFlooded()) {
                continue;
            }
            cell.setFlooded(true);
            cell.addTerrain(new models.map.Terrains.WaterTerrain());

            Plant plant = cell.getCurrentPlant();
            if (plant != null && !plant.isDead() && !plant.isAquatic() && !cell.hasPlatform()) {
                plant.getHealth().takeDamage(plant.getHealth().getMaxHp());   // swept away by the tide
            }
        }
        events.add(new Result(true, "The tide rises and floods column " + column + "."));
    }

    // Drains one column back to sand across every row.
    private void drainColumn(GameSession session, int column, List<Result> events) {
        if (column < 0) {
            return;
        }
        for (Row row : session.getMap().getRows()) {
            Cell cell = row.cellAt(column);
            cell.setFlooded(false);
            cell.getTerrain().removeIf(t -> t instanceof models.map.Terrains.WaterTerrain);
        }
        events.add(new Result(true, "The tide recedes from column " + column + "."));
    }

    // A flooded low-sand tile ('-') may let a zombie surface from beneath it. Rolled per flooded tile
    // each wave; a surfaced zombie walks like any other.
    private void surfaceLowTideZombies(GameSession session, List<Result> events) {
        for (Row row : session.getMap().getRows()) {
            for (Cell cell : row.getCells()) {
                boolean lowSand = cell.getTerrain().stream()
                        .anyMatch(t -> t instanceof models.map.Terrains.LowSandTerrain);
                if (!lowSand || !cell.isFlooded()) {
                    continue;
                }
                if (random.nextDouble() >= Constants.LOW_TIDE_SURFACE_PROBABILITY) {
                    continue;
                }
                int col = (int) Math.floor(cell.getX());
                Zombie surfaced = factories.ZombieFactory.createZombie("ZombieDefault", col + 0.5,
                        row.getIndex(), session);
                if (surfaced != null) {
                    row.getZombies().add(surfaced);
                    events.add(new Result(true, "A zombie surfaces from the low tide at (" + col + ", "
                            + row.getIndex() + ")."));
                }
            }
        }
    }

    // Frostbite freezing wind: each wave, a gust sweeps 1-2 random rows and chills every plant standing
    // in them. Three chills freeze a plant solid (Plant.takeIceHit), which is the "3 levels of chill,
    // level 3 = frozen" rule. Empty rows still get a gust line so the wind is always visible.
    private void applyFreezingWind(GameSession session, List<Result> events) {
        int rowCount = session.getMap().getRows().size();
        if (rowCount == 0) {
            return;
        }
        int gusts = 1 + random.nextInt(2);   // 1 or 2 rows
        List<Integer> chosen = new ArrayList<>();
        for (int i = 0; i < gusts; i++) {
            int row = random.nextInt(rowCount);
            if (chosen.contains(row)) {
                continue;
            }
            chosen.add(row);
            for (Cell cell : session.getMap().getRow(row).getCells()) {
                if (cell.hasPlant() && !cell.getCurrentPlant().isDead()) {
                    cell.getCurrentPlant().takeIceHit();
                }
            }
            events.add(new Result(true, "A freezing wind sweeps through row " + row + "."));
        }
    }

    // Ice melts at 60 HP/second (6 per tick) whenever a fire plant sits in one of the eight tiles
    // around it -- both a frozen plant's own ice block and a standalone frozen-terrain block. Fire
    // projectiles clear ice instantly; that path is handled where the projectile lands, not here.
    private void applyIceMelt(GameSession session) {
        int meltPerTick = Constants.MELT_RATE_PER_SECOND / Constants.TICKS_PER_SECOND;   // 60/10 = 6
        for (Row row : session.getMap().getRows()) {
            for (Cell cell : row.getCells()) {
                int cx = (int) Math.floor(cell.getX());
                int cy = cell.getY();
                if (!hasFirePlantAround(session, cx, cy)) {
                    continue;
                }
                if (cell.hasPlant() && cell.getCurrentPlant().isFrozen()) {
                    cell.getCurrentPlant().meltIceBlock(meltPerTick);
                }
                for (Terrain terrain : cell.getTerrain()) {
                    if (terrain instanceof FrozenTerrain && !terrain.isDestroyed()) {
                        ((FrozenTerrain) terrain).meltByTick();
                    }
                }
            }
        }
    }

    // Is a live fire plant in any of the eight tiles surrounding (x, y)? The centre tile itself does
    // not count -- a plant does not melt its own block.
    private boolean hasFirePlantAround(GameSession session, int x, int y) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) {
                    continue;
                }
                int nx = x + dx;
                int ny = y + dy;
                if (!session.getMap().isValidCoordinate(nx, ny)) {
                    continue;
                }
                Cell neighbour = session.getMap().getRow(ny).cellAt(nx);
                Plant plant = neighbour.getCurrentPlant();
                if (plant != null && !plant.isDead() && plant.isFirePlant()) {
                    return true;
                }
            }
        }
        return false;
    }

    // A slider tile shoves a zombie standing on it to the row above or below (whichever the tile is
    // set to). Dodo Riders are exempt -- they fly, so the ground cannot move them. The lane switch is
    // idempotent: MovementComponent.startLaneSwitch ignores a request that is already under way or that
    // targets the row the zombie is already on, so re-checking the same tile every tick is harmless.
    private void applySliderTiles(GameSession session) {
        for (Row row : session.getMap().getRows()) {
            for (Zombie zombie : new ArrayList<>(row.getZombies())) {
                if (zombie.getHealth().isDead() || !zombie.isOnBoard() || isDodoRider(zombie)) {
                    continue;
                }
                int column = (int) Math.floor(zombie.getMovement().getPositionX());
                if (!session.getMap().isValidCoordinate(column, zombie.getMovement().getPositionY())) {
                    continue;
                }
                Cell cell = session.getMap().getRow(zombie.getMovement().getPositionY()).cellAt(column);
                for (Terrain terrain : cell.getTerrain()) {
                    if (terrain instanceof SlipTerrain && !terrain.isDestroyed()) {
                        terrain.effect(zombie, null);
                    }
                }
            }
        }
    }

    // The Dodo Rider flies over ground hazards. Identified by its alias/category token rather than a
    // dedicated flag, the same way the rest of the code keys off the zombie's type name.
    private boolean isDodoRider(Zombie zombie) {
        String alias = zombie.getAlias();
        String category = zombie.getCategory();
        return (alias != null && alias.toLowerCase().contains("dodo"))
                || (category != null && category.toLowerCase().contains("dodo"));
    }
}
