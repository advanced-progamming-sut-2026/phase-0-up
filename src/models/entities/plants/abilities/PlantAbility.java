package models.entities.plants.abilities;

import models.entities.plants.Plant;
import models.entities.plants.abilities.triggers.TriggerStrategy;
import models.entities.zombies.Zombie;
import models.entities.plants.abilities.triggers.ObstacleSight;
import models.entities.projectiles.Projectile;
import models.entities.projectiles.Trajectory;
import models.game.GameSession;

public abstract class PlantAbility {
    protected int cooldownTimer;
    protected int actionInterval;

    protected TriggerStrategy triggerStrategy;

    public PlantAbility(int actionInterval, TriggerStrategy triggerStrategy) {
        this.actionInterval = actionInterval;
        this.cooldownTimer = actionInterval;
        this.triggerStrategy = triggerStrategy;
    }


    public void update(Plant owner, GameSession gameSession){
        if (cooldownTimer > 0) {
            cooldownTimer--;
        }

        if (cooldownTimer <= 0){
            if (canExecute(owner,  gameSession)) {
                execute(owner, gameSession);
                cooldownTimer = actionInterval;
            }
        }
    }

    public boolean canExecute(Plant owner,  GameSession gameSession){
        if (triggerStrategy == null) return false;

        return triggerStrategy.canTrigger(owner, gameSession);
    }
    public abstract void execute(Plant owner,  GameSession gameSession);

    // Whether this ability has committed to acting but its effect has not landed yet -- the plant is
    // visibly drawing back. Abilities that take effect the instant they trigger are never winding up.
    //
    // A wind-up costs the simulation nothing: the ability still fires once per actionInterval, so only
    // the moment WITHIN the cycle shifts. What it buys is a window the view can play an animation in,
    // ending on the effect rather than starting after it.
    public boolean isWindingUp() {
        return false;
    }

    // Whether this ability's cooldown has run out, so it would act the moment its trigger allowed it.
    //
    // For the view: a plant that takes nine seconds to build a shot is not resting for nine seconds,
    // it is CHARGING, and Citron's art ships a seven-second `charge` clip that nothing could ask for
    // without this.
    public boolean isReady() {
        return cooldownTimer <= 0;
    }

    // Whether a plant-food boost handed to THIS ability still has work left to do -- shots queued, a
    // flurry running, a ball waiting to roll. Abilities whose boost lands the instant it is given
    // (an armour grant, a lane freeze) are never busy, and the default says so.
    //
    // This is the whole of what the view knows about how long a boost lasts. Before it existed the
    // animation ran for a guessed number of seconds and stopped there, which is why a Snow Pea kept
    // firing for another two and a half seconds after its glow had already faded: sixty shots a tick
    // apart is six seconds of boost against a three-and-a-half second animation. Anything that queues
    // plant-food work MUST report it here, or the same gap opens again for that plant.
    public boolean isPlantFoodBusy() {
        return false;
    }

    // Called once when the owning plant dies, before removal. Default: no death behavior.
    public void onOwnerDeath(Plant owner, GameSession gameSession) {
        // no-op
    }

    // Called each time a zombie lands a bite on the owning plant. Default: no on-eaten behavior.
    public void onOwnerEaten(Plant owner, Zombie eater, GameSession gameSession) {
        // no-op
    }

    // Zombies first, graves only when the lane is empty of them.
    //
    // A -pult arcs over terrain, so left alone its shot passes through a headstone without touching it.
    // That is correct while there is a zombie further down the lane -- arcing over the grave to reach it
    // is exactly what the plant is for -- and useless in a lane holding nothing but graves, where the
    // plant fires forever and never reopens the lane. So the shot is marked to come down on the grave in
    // that second case alone, which makes "the zombie wins" structural rather than a rule to remember:
    // the mark is never set while a target is in sight.
    //
    // Lives here rather than on one ability because two of them lob: ShootProjectileAbility for the
    // -pult family in general and KernelPultAbility for the corn, which builds its own Projectile.
    // A DIRECT shot is left alone -- it damages terrain on contact either way.
    protected static void aimLobAtObstacle(Plant owner, GameSession gameSession, Projectile projectile,
                                        Trajectory trajectory, boolean backward) {
        if (trajectory != Trajectory.LOBBED || backward || projectile == null) {
            return;
        }
        if (zombieAhead(owner, gameSession)) {
            return;
        }
        double graveX = ObstacleSight.nearestObstacleAheadX(owner, gameSession);
        if (graveX >= 0.0) {
            projectile.setTerrainTarget(graveX);
        }
    }

    // The same question ForwardStandardTrigger asks, asked again at the moment the shot actually leaves.
    // It has to be re-asked: a shooter's trigger fires several ticks before its wind-up ends, and a
    // zombie can walk into the lane in between -- in which case this shot should arc over the grave at
    // it rather than bury itself in the headstone in front.
    protected static boolean zombieAhead(Plant owner, GameSession gameSession) {
        java.util.List<models.entities.zombies.Zombie> lane =
                gameSession.getMap().getRow(owner.getY()).getZombies();
        if (lane == null) {
            return false;
        }
        for (models.entities.zombies.Zombie zombie : lane) {
            if (zombie.isTargetable() && zombie.getMovement().getPositionX() > owner.getX()) {
                return true;
            }
        }
        return false;
    }
}
