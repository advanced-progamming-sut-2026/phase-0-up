package models.entities.zombies.Abilities;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.map.Cell;
import models.map.IceBlock;
import models.map.Row;

import java.util.ArrayList;
import java.util.List;

public class PushIceAbility implements ZombieAbility {
    private boolean hasIceBlocks = true;
    private static final double COLLISION_THRESHOLD = 0.4;

    // One tile per block. A Troglobite spawns with three (NumberOfIceblocksToSpawnWith in zombies.json),
    // so it arrives shoving a three-tile wall of ice ahead of itself, and the wall gets a tile shorter
    // each time a block goes.
    private static final double BLOCK_SPACING = 1.0;

    // What comes out when one breaks. The game calls it a Yeti Imp -- zombies.json even names the type
    // ("ImpType": "iceage_imp") -- but this roster has exactly one imp, so that is what is freed. The
    // view draws it in its Frostbite coat; see ZombieRenderer.spriteNameFor.
    private static final String IMP_ALIAS = "ZombieImp";

    // Blocks still standing, as of last tick. The count lives on the health stack (one ICE_BLOCK layer
    // each) and this is only the memory of it, so a block that has just been broken can be noticed and
    // its passenger let out.
    private int blocksLastTick = -1;

    @Override
    public void execute(Zombie troglobite) {
        if (troglobite == null || !hasIceBlocks) {
            return;
        }

        // Above the "can it move" check: a block breaks when it is shot, not when its pusher takes a
        // step, so a Troglobite frozen or buttered mid-lane still lets its passengers out.
        releaseImpsForBrokenBlocks(troglobite);

        if (troglobite.getState().isUnableToMove()) {
            return;
        }

        if (!troglobite.getHealth().hasArmor()) {
            triggerIceDestroyed(troglobite);
            return;
        }
        crushObstaclesInFront(troglobite);
    }

    // How many blocks a Troglobite still has in front of it.
    public static int blocksLeft(Zombie troglobite) {
        if (troglobite == null || troglobite.getHealth() == null) {
            return 0;
        }
        int blocks = 0;
        for (models.entities.zombies.Components.HealthLayer layer : troglobite.getHealth().getLayers()) {
            if (layer.getType() == models.entities.zombies.Components.ArmorType.ICE_BLOCK) {
                blocks++;
            }
        }
        return blocks;
    }

    // Where one of the blocks sits, as a continuous x.
    //
    // They fill the tiles immediately ahead, nearest to the zombie first, so with three left the wall
    // runs from one tile ahead to three. `index` counts from the zombie outward, which makes index
    // blocksLeft-1 the LEADING block -- the one that reaches a plant first, and the one that breaks
    // first, since a shot coming down the lane meets it before the others.
    public static double blockX(Zombie troglobite, int index) {
        double x = troglobite.getMovement().getPositionX();
        double reach = BLOCK_SPACING * (index + 1);
        return troglobite.getMovement().isMovingRight() ? x + reach : x - reach;
    }

    // Whether this Troglobite's wall covers a given tile -- what a Hot Potato planted on a block asks.
    public static boolean coversTile(Zombie troglobite, double cellX) {
        for (int i = 0; i < blocksLeft(troglobite); i++) {
            if (Math.abs(blockX(troglobite, i) - cellX) <= 0.5) {
                return true;
            }
        }
        return false;
    }

    // A Yeti Imp per block that has gone since last tick.
    //
    // The block is the imp's ride: breaking one does not merely remove armour, it lets something out.
    // Noticed by counting rather than by a callback from HealthComponent, because a block can be broken
    // by a pea, a blast, a mower or a Hot Potato, and every one of those goes through the health stack.
    private void releaseImpsForBrokenBlocks(Zombie troglobite) {
        int blocks = blocksLeft(troglobite);
        if (blocksLastTick < 0) {
            blocksLastTick = blocks;   // first tick: nothing has broken yet
            return;
        }
        for (int i = blocks; i < blocksLastTick; i++) {
            // At the block's own tile, which is where the player watched it shatter -- the outermost
            // one that has just gone, so the imps come out front first.
            spawnImp(troglobite, blockX(troglobite, i));
        }
        blocksLastTick = blocks;
    }

    private void spawnImp(Zombie troglobite, double x) {
        int row = troglobite.getMovement().getPositionY();
        Zombie imp = factories.ZombieFactory.createZombie(IMP_ALIAS, x, row,
                troglobite.getGameSession());
        if (imp == null) {
            return;
        }
        troglobite.getGameSession().getMap().getRow(row).getZombies().add(imp);
        troglobite.getGameSession().reportEvent("A frozen block shatters at (" + (int) x + ", " + row
                + ") and the Yeti Imp inside it hits the ground running!");
    }

    private void crushObstaclesInFront(Zombie troglobite) {
        if (troglobite.getGameSession() == null || troglobite.getGameSession().getMap() == null) {
            return;
        }

        int rowIdx = troglobite.getMovement().getPositionY();
        // The LEADING block's tile, not the zombie's. What crushes a plant is the front face of the
        // wall, which is up to three tiles ahead of the Troglobite -- crushing from under its own feet
        // meant the ice reached a plant, stopped on it, and did nothing until the zombie itself arrived.
        int blocks = blocksLeft(troglobite);
        double zX = blocks > 0 ? blockX(troglobite, blocks - 1)
                : troglobite.getMovement().getPositionX();

        Row row = troglobite.getGameSession().getMap().getRow(rowIdx);
        if (row != null && row.getCells() != null) {
            for (Cell cell : row.getCells()) {
                if (cell != null && cell.getCurrentPlant() != null) {
                    Plant plant = cell.getCurrentPlant();
                    if (!plant.isDead()) {
                        double distance = Math.abs(zX - cell.getX());

                        if (distance <= COLLISION_THRESHOLD) {
                            if (plant.getHealth() != null) {
                                plant.getHealth().takeDamage(Integer.MAX_VALUE);
                            }
                            troglobite.getGameSession().reportEvent("The Troglobite's ice block crushes "
                                    + plant.getName() + " at (" + (int) cell.getX() + ", " + rowIdx + ").");
                        }
                    }
                }
            }
        }
        List<Zombie> allZombies = new ArrayList<>();
        for(Row r : troglobite.getGameSession().getMap().getRows()){
            for(Zombie z : r.getZombies()){
                allZombies.add(z);
            }
        }

        if (allZombies != null) {
            for (Zombie otherZombie : allZombies) {
                if (otherZombie != troglobite &&
                        !otherZombie.getHealth().isDead() &&
                        otherZombie.getState().isHypnotized() &&
                        otherZombie.getMovement().getPositionY() == rowIdx) {

                    double distance = Math.abs(zX - otherZombie.getMovement().getPositionX());

                    if (distance <= COLLISION_THRESHOLD) {
                        otherZombie.getHealth().applyDamage(Integer.MAX_VALUE , null , null);
                        troglobite.getGameSession().reportEvent("The Troglobite's ice block crushes a "
                                + "hypnotized zombie at (" + (int) otherZombie.getX() + ", " + rowIdx + ").");
                    }
                }
            }
        }
    }

    private void triggerIceDestroyed(Zombie troglobite) {
        this.hasIceBlocks = false;
        troglobite.getGameSession().reportEvent("The Troglobite's ice blocks are gone at ("
                + (int) troglobite.getX() + ", " + troglobite.getY() + "); it now walks and eats normally.");
    }

    public void onTroglobiteDeath(Zombie troglobite) {
        if (hasIceBlocks && troglobite.getGameSession() != null) {
            int row = troglobite.getMovement().getPositionY();
            double x = troglobite.getMovement().getPositionX();
            troglobite.getGameSession().getMap().getRow(row).addObstacle(
                    new IceBlock(troglobite.getHealth().getTotalHP() , x , row, troglobite.getGameSession())
            );
            troglobite.getGameSession().reportEvent("The Troglobite falls at (" + (int) x + ", " + row
                    + "), leaving its ice block behind as an obstacle.");
        }
    }

    public boolean hasIceBlocks() {
        return hasIceBlocks;
    }
}