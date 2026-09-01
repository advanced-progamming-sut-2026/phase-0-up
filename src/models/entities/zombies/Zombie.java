package models.entities.zombies;

import models.entities.Entity;
import models.entities.plants.Plant;
import models.entities.zombies.Abilities.ZombieAbility;
import models.entities.zombies.Components.ActionState;
import models.entities.zombies.Components.ArmorType;
import models.entities.zombies.Components.HealthComponent;
import models.entities.zombies.Components.MovementComponent;
import models.entities.zombies.Components.StateComponent;
import models.game.GameSession;
import utils.Constants;

import java.util.ArrayList;
import java.util.List;

public class Zombie extends Entity {
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
    // The tick this zombie was created on. Scoring rules that care how long a zombie survived (the
    // scoring mode's speed-kill bonus) measure against it; -1 when it was built without a live session.
    private final long spawnTick;

    public Zombie(int id, String category, int baseHp, List<ArmorType> armorTypes, String alias,
                  int eatDamage, int eatSpeed, double speed, double startX, int startY, boolean canSpawnPlantFood,
                  List<ZombieAbility> abilities, int wavePointCost, boolean glowing, GameSession gameSession) {
        super(alias, id, startX, startY);
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
        this.abilities = (abilities != null) ? abilities : new ArrayList<>();
        this.wavePointCost = wavePointCost;
        this.glowing = glowing;
        this.state = new StateComponent();
        this.health = new HealthComponent(baseHp, armorTypes, this);
        this.movement = new MovementComponent(speed, startX, startY, state);
        this.gameSession = gameSession;
        this.spawnTick = gameSession != null ? gameSession.getTimeTicks() : -1L;
    }

    // The tick this zombie walked into the world on, or -1 if it was built outside a session.
    public long getSpawnTick() {
        return spawnTick;
    }

    // Ticks the zombie once per frame: status timers decay, abilities (eating/attacks/specials) run,
    // then it moves (movement itself respects freeze/butter/eating via the state component).
    @Override
    public void update(GameSession session) {
        if (health.isDead()) {
            state.setAction(ActionState.DYING);
            return;
        }
        state.update();
        health.update();

        // Butter STUNS: a buttered zombie does nothing at all until it wears off.
        //
        // Gated centrally here rather than left to each ability, the same way Plant.update gates on
        // isDisabled(). Most abilities did check isUnableToMove() for themselves and butter is part of
        // that answer -- but "most" is the problem: the check was a convention rather than a rule, so
        // whether a stunned zombie kept working depended on which zombie it was, and any ability added
        // later would have started out ignoring it. A Kernel-pult's butter is supposed to be the same
        // half-second of nothing whoever it lands on.
        //
        // ABOVE the ability loop and above movement, but BELOW the two component updates: the butter
        // timer itself lives in state.update() and would never run down if this returned before it, and
        // poison already in a zombie's blood keeps working on a stunned one -- being held still is not
        // being protected.
        if (state.isButtered()) {
            return;
        }

        for (ZombieAbility ability : abilities) {
            ability.execute(this);
        }
        movement.move();
    }

    // Scales bite damage only, leaving health alone (the Newspaper Zombie's rage).
    public void scaleEatDamage(double multiplier) {
        if (multiplier > 0) {
            this.eatDamage = Math.max(1, (int) Math.round(this.eatDamage * multiplier));
        }
    }
    // Hypno-shroom upgrades: a hypnotized ally fights with buffed HP and bite damage.
    public void applyHypnoBuffs(double hpMultiplier, double damageMultiplier) {
        this.eatDamage = (int) Math.round(this.eatDamage * damageMultiplier);
        health.scaleHp(hpMultiplier);
    }

    // Position is owned by the movement component; expose it through the Entity contract.
    @Override
    public double getX() { return movement.getPositionX(); }
    @Override
    public int getY() { return movement.getPositionY(); }

    public void addAbility(ZombieAbility ability) {
        if (ability != null) {
            abilities.add(ability);
        }
    }

    public StateComponent getState() { return state; }
    public MovementComponent getMovement() { return movement; }

    public void setWavePointCost(int wavePointCost) { this.wavePointCost = wavePointCost; }
    public int getWavePointCost() { return wavePointCost; }

    // The coin, gem or pot this zombie is carrying, or null for the great majority that carry nothing.
    //
    // Decided at SPAWN (ZombieFactory) rather than at death, which is where the roll used to live. The
    // view has to be able to mark a carrier while it is still walking, and a die rolled in the death
    // handler cannot be read by anything before that handler runs. Same reasoning, and the same place,
    // as `glowing` -- which is the plant-food carrier and was already settled at birth.
    //
    // Never serialised: zombies live and die inside a level and no save holds one.
    private models.entities.collectibles.Collectibles carriedDrop;

    public models.entities.collectibles.Collectibles getCarriedDrop() { return carriedDrop; }

    public void setCarriedDrop(models.entities.collectibles.Collectibles drop) {
        this.carriedDrop = drop;
    }

    // Whether this zombie is worth marking on the board at all -- plant food or one of the three loot
    // drops. The view asks this rather than testing two fields, so "special" means one thing.
    public boolean carriesSomething() {
        return glowing || carriedDrop != null;
    }

    public void setGlowing(boolean glowing) { this.glowing = glowing; }
    public boolean isGlowing() { return glowing; }

    public List<ZombieAbility> getAbilities() { return abilities; }
    public HealthComponent getHealth() { return health; }

    public GameSession getGameSession() {
        return gameSession;
    }

    // Whether the zombie is standing on the grid. It spawns one cell beyond the right edge and walks
    // in, and can be shoved past either end mid-fight, so for part of its life it is off the board:
    // not a target, and with nothing under it to eat.
    public boolean isOnBoard() {
        double x = movement.getPositionX();
        return x >= 0 && x < Constants.BOARD_COLS;
    }

    private boolean untargetable = false;
    public void setUntargetable(boolean untargetable) { this.untargetable = untargetable; }
    public boolean isUntargetable() { return untargetable; }
    // A hypnotized zombie is fighting for the player, so nothing plant-side may act on it.
    public boolean isTargetable() {
        // Airborne is not targetable: a Prospector riding its own dynamite across the lawn is over the
        // lane, not in it, so shots pass under it and nothing aims at it. Without this a plant could
        // shoot a zombie out of the sky halfway through a flight it is not standing anywhere for.
        return !health.isDead() && isOnBoard() && !untargetable && !state.isHypnotized()
                && !state.isAirborne();
    }

    // A zombie spawns one cell beyond the right edge and can be shoved past either end mid-fight, so
    // its column is only a valid cell index part of the time; off the board there is nothing to eat.
    public Plant getTargetPlantInFront() {
        int column = (int) Math.floor(movement.getPositionX());
        if (!gameSession.getMap().isValidCoordinate(column, movement.getPositionY())) {
            return null;
        }
        return gameSession.getMap().getRow(movement.getPositionY()).cellAt(column).getCurrentPlant();
    }

    public int getEatDamage() {
        return eatDamage;
    }

    public int getEatSpeed() {
        return eatSpeed;
    }

    public boolean isCanSpawnPlantFood() {
        return canSpawnPlantFood;
    }

    public String getAlias() {
        return alias;
    }

    public String getCategory() {
        return category;
    }
}
