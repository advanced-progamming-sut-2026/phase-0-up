package models.entities.zombies.Abilities;

import factories.ZombieFactory;
import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.map.Barrel;
import models.map.Cell;

public class RollTheBarrel implements ZombieAbility {
    private boolean isBarrelIntact = true;
    // How close a plant must be for the rolling barrel to flatten it, in TILES. The board is nine
    // columns wide, so the old 35.0 matched every plant in the row and wiped the whole lane on the
    // first tick -- this is a contact reach, not a screen-space distance.
    private static final double COLLISION_THRESHOLD = 0.7;
    // Where the two Imps land relative to the burst barrel, again in tiles (was +/-10, i.e. far off
    // the board, so they were placed somewhere they could never walk back from).
    private static final double IMP_SPREAD = 1.0;

    @Override
    public void execute(Zombie roller) {
        if (!isBarrelIntact || roller.getState().isUnableToMove()) {
            return;
        }

        if (!roller.getHealth().hasArmor()) {
            triggerBarrelDestroyed(roller);
            return;
        }
        crushPlantsInFront(roller);
    }


    private void crushPlantsInFront(Zombie roller) {
        int row = roller.getMovement().getPositionY();
        double zX = roller.getMovement().getPositionX();

        for (Cell cell : roller.getGameSession().getMap().getRow(row).getCells()) {
            Plant plant = cell.getCurrentPlant();
            if (plant != null && !plant.isDead()) {
                double distance = Math.abs(zX - plant.getX());

                if (distance <= COLLISION_THRESHOLD) {
                    if (plant.getHealth() != null) {
                        plant.getHealth().takeDamage(Integer.MAX_VALUE);
                    }
                    roller.getGameSession().reportEvent("The rolling barrel crushes " + plant.getName()
                            + " at (" + (int) plant.getX() + ", " + row + ").");
                }
            }
        }
    }


    private void triggerBarrelDestroyed(Zombie roller) {
        this.isBarrelIntact = false;

        int row = roller.getMovement().getPositionY();
        double currentX = roller.getMovement().getPositionX();

        // Land both Imps on the board either side of the barrel, and never add a null to the row: a
        // missing blueprint would otherwise plant a null that every later sweep would trip over.
        int spawned = 0;
        double maxX = utils.Constants.BOARD_COLS - 0.5;
        for (double offset : new double[]{-IMP_SPREAD, IMP_SPREAD}) {
            double impX = Math.max(0.5, Math.min(maxX, currentX + offset));
            Zombie imp = ZombieFactory.createZombie("ZombieImp", impX, row, roller.getGameSession());
            if (imp != null) {
                roller.getGameSession().getMap().getRow(row).getZombies().add(imp);
                spawned++;
            }
        }

        roller.getGameSession().reportEvent("The barrel bursts open at (" + (int) currentX + ", " + row
                + ") and " + spawned + " Imps tumble out.");
    }

    public void onRollerDeath(Zombie roller) {
        if (isBarrelIntact) {
            int row = roller.getMovement().getPositionY();
            double x = roller.getMovement().getPositionX();
            Barrel b = new Barrel(roller.getHealth().getLayers().pop().getCurrentHp(), x, row,
                    roller.getGameSession());
            roller.getGameSession().getMap().getRow(row).addObstacle(b);

            roller.getGameSession().reportEvent("The Barrel Roller falls at (" + (int) x + ", " + row
                    + "), leaving its barrel behind as a shield.");
        }
    }

    public boolean isBarrelIntact() {
        return isBarrelIntact;
    }
}