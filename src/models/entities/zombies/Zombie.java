package models.entities.zombies;

import models.entities.plants.Entity;
import models.entities.zombies.Abilities.ZombieAbility;
import models.entities.zombies.Components.HealthComponent;
import models.entities.zombies.Components.MovementComponent;
import models.entities.zombies.Components.StateComponent;

import java.util.List;

public class Zombie extends Entity {
    private HealthComponent health;
    private MovementComponent movement;
    private StateComponent state;
    private List<ZombieAbility> abilities;
    private int wavePointCost;
    private boolean glowing;


    public Zombie(HealthComponent health) {}
    public void addAbility(ZombieAbility ability) {}
    public void update() {}
    public StateComponent getState() { return state; }
    public MovementComponent getMovement() { return movement; }
    public void setWavePointCost(int wavePointCost) {}
    public int getWavePointCost() {return wavePointCost;}
    public void setGlowing(boolean glowing) {}
    public boolean isGlowing() {return glowing;}
    public List<ZombieAbility> getAbilities() {return abilities;}
    public HealthComponent getHealth() {return health;}
}
