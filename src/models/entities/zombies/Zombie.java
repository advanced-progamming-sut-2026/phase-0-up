package models.entities.zombies;

import models.entities.Entity;
import models.entities.zombies.Abilities.ZombieAbility;
import models.entities.zombies.Components.HealthComponent;
import models.entities.zombies.Components.MovementComponent;
import models.entities.zombies.Components.StateComponent;

import java.util.List;

public class Zombie{
    private HealthComponent health;
    private MovementComponent movement;
    private StateComponent state;
    private List<ZombieAbility> abilities;
    private int wavePointCost;
    private boolean glowing;
    private boolean isHypnotized;


    public Zombie(HealthComponent health) {}
    public void addAbility(ZombieAbility ability) {}
    public StateComponent getState() { return state; }
    public MovementComponent getMovement() { return movement; }
    public void setWavePointCost(int wavePointCost) {}
    public int getWavePointCost() {return wavePointCost;}
    public void setGlowing(boolean glowing) {}
    public boolean isGlowing() {return glowing;}
    public List<ZombieAbility> getAbilities() {return abilities;}
    public HealthComponent getHealth() {return health;}

    public boolean isHypnotized() {
        return isHypnotized;
    }

    public void applyHypnotize() {
        //TODO: reverse zombie movement and make it attack other zombies
    }

    //TODO : add stun effect
    //TODO : add ice pea slow effect
}
