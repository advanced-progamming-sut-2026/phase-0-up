package models.entities.zombies.Abilities;

import factories.ZombieFactory;
import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.map.Barrel;
import models.map.Cell;

public class RollTheBarrel implements ZombieAbility {
    private boolean isBarrelIntact = true;
    private static final double COLLISION_THRESHOLD = 35.0;

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
                    System.out.println("Barrel crushed a plant at X: " + plant.getX());
                }
            }
        }
    }


    private void triggerBarrelDestroyed(Zombie roller) {
        this.isBarrelIntact = false;

        int row = roller.getMovement().getPositionY();
        double currentX = roller.getMovement().getPositionX();

        Zombie z1 = ZombieFactory.createZombie("ZombieImp" , currentX-10 , row);
        Zombie z2 = ZombieFactory.createZombie("ZombieImp" , currentX+10 , row);
        roller.getGameSession().getMap().getRow(row).getZombies().add(z1);
        roller.getGameSession().getMap().getRow(row).getZombies().add(z2);

        System.out.println("Barrel destroyed! Two Imps spawned in row " + row);

    }

    public void onRollerDeath(Zombie roller) {
        if (isBarrelIntact) {
            int row = roller.getMovement().getPositionY();
            double x = roller.getMovement().getPositionX();
            Barrel b = new Barrel(roller.getHealth().getLayers().pop().getCurrentHp() , x , row);
            roller.getGameSession().getMap().getRow(row).addObstacle(b);

            System.out.println("Roller died! Barrel left behind as a shield at X: " + x);
        }
    }

    public boolean isBarrelIntact() {
        return isBarrelIntact;
    }
}