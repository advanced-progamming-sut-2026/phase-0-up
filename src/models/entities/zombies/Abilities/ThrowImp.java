package models.entities.zombies.Abilities;

import factories.ZombieFactory;
import models.entities.zombies.Zombie;

public class ThrowImp implements ZombieAbility{
    private boolean hasThrownImp;

    public ThrowImp() {
        this.hasThrownImp = false;
    }

    public void setHasThrownImp(boolean hasThrownImp) {
        this.hasThrownImp = hasThrownImp;
    }

    @Override
    public void execute(Zombie zombie) {
        if(zombie.getHealth().getTotalHP() > 50) return;
        if(!hasThrownImp){
            Zombie z = ZombieFactory.createZombie(
                    "ZombieImp", 2.5, zombie.getMovement().getPositionY(), zombie.getGameSession());
            zombie.getGameSession().getMap().getRow(z.getMovement().getPositionY()).getZombies().add(z);
            setHasThrownImp(true);
        }
    }
}
