package models.entities.zombies;

import models.entities.Entity;
import models.entities.plants.Plant;
import models.entities.zombies.Abilities.ZombieAbility;
import models.entities.zombies.Components.ArmorType;
import models.entities.zombies.Components.HealthComponent;
import models.entities.zombies.Components.MovementComponent;
import models.entities.zombies.Components.StateComponent;
import models.game.GameSession;

import java.util.List;

public class Zombie{
    private int id;
    private String category;
    private int baseHp;
    private List<ArmorType> armorTypes;
    private String alias;
    private int eatDamage;
    private int eatSpeed;
    private double speed;
    private double startX;
    private int startY;
    private boolean canSpawnPlantFood;
    private HealthComponent health;
    private MovementComponent movement;
    private StateComponent state;
    private List<ZombieAbility> abilities;
    private int wavePointCost;
    private boolean glowing;
    private GameSession gameSession;


    public Zombie(int id, String category, int baseHp, List<ArmorType> armorTypes, String alias,
                  int eatDamage, int eatSpeed, double speed, double startX, int startY, boolean canSpawnPlantFood,
                  List<ZombieAbility> abilities, int wavePointCost, boolean glowing, GameSession gameSession) {
        this.id = id;
        this.category = category;
        this.baseHp = baseHp;
        this.armorTypes = armorTypes;
        this.alias = alias;
        this.eatDamage = eatDamage;
        this.eatSpeed = eatSpeed;
        this.speed = speed;
        this.startX = startX;
        this.startY = startY;
        this.canSpawnPlantFood = canSpawnPlantFood;
        this.abilities = abilities;
        this.wavePointCost = wavePointCost;
        this.glowing = glowing;
        this.state = new StateComponent();
        this.health = new HealthComponent(armorTypes , this);
        this.movement = new MovementComponent(speed , startX , startY , state);
        this.gameSession = gameSession;
    }

    public void addAbility(ZombieAbility ability) {}
    public StateComponent getState() { return state; }
    public MovementComponent getMovement() { return movement; }
    public void setWavePointCost(int wavePointCost) {}
    public int getWavePointCost() {return wavePointCost;}
    public void setGlowing(boolean glowing) {}
    public boolean isGlowing() {return glowing;}
    public List<ZombieAbility> getAbilities() {return abilities;}
    public HealthComponent getHealth() {return health;}

    public GameSession getGameSession() {
        return gameSession;
    }

    public Plant getTargetPlantInFront() {
        return gameSession.getMap().getRow(movement.getPositionY()).cellAt((int) movement.getPositionX()).getCurrentPlant();
    }

    public int getEatDamage() {
        return eatDamage;
    }

    public String getAlias() {
        return alias;
    }
}
