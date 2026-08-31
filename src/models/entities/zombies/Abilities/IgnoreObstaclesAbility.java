package models.entities.zombies.Abilities;

import models.entities.plants.Plant;
import models.entities.plants.PlantTags;
import models.entities.zombies.Zombie;
import models.map.Cell;
import models.map.Row;

public class IgnoreObstaclesAbility implements ZombieAbility {

    // How close a plant has to be for the rider to be over it, in tiles either side.
    //
    // 0.5 -- the plant's own tile, exactly -- is where this started, and it is the smallest window that
    // can possibly work: the rider left the ground at the tile's edge and touched down at the far edge.
    // That is a hop of precisely the obstacle's width, which reads as clipping through the top of it
    // rather than clearing it, and it gave `fly_start` and `fly_end` no room before and after the
    // crossing.
    //
    // 0.8 lifts the rider about a third of a tile early and sets it down a third late. At the horde's
    // pace (0.185 x ZOMBIE_SPEED_SCALE = 0.02 tiles a tick, so five seconds to cross one tile) that is
    // roughly 1.5 extra seconds at each end -- comfortably more than the 0.97s take-off and the 1.5s
    // landing need, so both play out in the air instead of being cut off by the ground.
    //
    // Symmetric on purpose: a hypnotised rider walks the other way, and a window measured from the
    // zombie rather than from its heading needs no branch for that.
    private static final double REACH_TILES = 0.8;

    @Override
    public void execute(Zombie zombie) {
        if (zombie == null || zombie.getState().isUnableToMove()) {
            return;
        }
        Plant plantInFront = getPlantInFront(zombie);

        // The flag stops EatPlantAbility chewing, and tells the view to play the jump.
        zombie.getState().setFlying(plantInFront != null && fliesOver(plantInFront));
    }

    // Would this zombie be over that plant rather than on it?
    //
    // Asked directly, by the one consumer that cannot use the flag: a mine's ContactTrigger runs in
    // CombatSystem's PLANT pass, which is two steps before the zombie pass that sets it. So on the tick a
    // Dodo Rider first came within reach of a Potato Mine the flag still held last tick's answer -- false
    // -- and the mine went off under a zombie that was supposed to be in the air. The flag was correct a
    // fiftieth of a second later, by which time the rider was in pieces.
    //
    // The fix is not to reorder the tick (every other plant wants to act before the zombies move) but to
    // stop reading a cached answer at the one moment it is guaranteed to be stale. The rule itself is
    // below and shared, so the two can never disagree about what a Dodo Rider clears.
    public static boolean fliesOver(Zombie zombie, Plant plant) {
        if (zombie == null || plant == null || zombie.getState().isUnableToMove()) {
            return false;
        }
        boolean canFly = zombie.getAbilities() != null && zombie.getAbilities().stream()
                .anyMatch(ability -> ability instanceof IgnoreObstaclesAbility);
        return canFly && flyOverable(plant);
    }

    // The spec's obstacles: the WALL_NUT family, lane-shunting plants and mines. A Tall-nut stops it.
    private boolean fliesOver(Plant plant) {
        return flyOverable(plant);
    }

    private static boolean flyOverable(Plant plant) {
        if (plant.getName() != null && plant.getName().equalsIgnoreCase("Tall-nut")) {
            return false;
        }
        if ("WALL_NUT".equalsIgnoreCase(plant.getCategory())) {
            return true;
        }
        return plant.getTags() != null && (plant.getTags().contains(PlantTags.MOVE_ZOMBIES)
                || plant.getTags().contains(PlantTags.TRAP));
    }

    private Plant getPlantInFront(Zombie zombie) {
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

        // Nearest FIRST, so a rider with two plants under its window commits to the one it is walking
        // into rather than the one it has just cleared. At this reach the window spans two tiles for
        // most of its length, and the tie matters: coming down onto a Peashooter has to beat still
        // being over the Wall-nut behind it, or the rider glides over a plant it should be eating.
        Plant nearest = null;
        double nearestGap = REACH_TILES;
        for (Cell cell : row.getCells()) {
            // Reach in TILES: the old 35.0 matched every plant in the row, not the one it reached.
            if (cell != null && cell.getDefendingPlant() != null && !cell.getDefendingPlant().isDead()) {
                double gap = Math.abs(zX - cell.getX());
                if (gap <= nearestGap) {
                    nearestGap = gap;
                    nearest = cell.getDefendingPlant();
                }
            }
        }
        return nearest;
    }
}