package models.entities.plants.abilities;

import models.entities.plants.Plant;
import models.entities.plants.abilities.triggers.TriggerStrategy;
import models.entities.projectiles.Element;
import models.entities.projectiles.Projectile;
import models.entities.projectiles.ProjectileType;
import models.entities.projectiles.Trajectory;
import models.game.GameSession;

public class BowlingBulbAbility extends PlantAbility implements VariantAction {

    private int cyanDamage = 40;
    private int blueDamage = 120;
    private int orangeDamage = 180;

    private int cyanReloadTicks;
    private int blueReloadTicks;
    private int orangeReloadTicks;

    private int cyanTimer = 0;
    private int blueTimer = 0;
    private int orangeTimer = 0;

    private boolean hasCyan = true;
    private boolean hasBlue = true;
    private boolean hasOrange = true;

    // Which bulb the plant is loaded with, one-based: 1 cyan, 2 blue, 3 orange. The art is drawn once
    // per colour on both sides -- the plant swings with `special`, `special2`, `special3` and the bulb
    // itself is BOWLINGBULB_PROJECTILE1/2/3 -- and only this ability knows which one is going out.
    private static final int CYAN = 1;
    private static final int BLUE = 2;
    private static final int ORANGE = 3;

    // Ticks spent visibly winding the bulb up before it rolls. The plant ships a swing per colour and
    // nothing ever played any of them: the renderer starts an action clip on the rising edge of
    // isWindingUp() and this ability never reported one, so bulbs rolled out of a motionless plant.
    private static final int WIND_UP_TICKS = 4;
    private int windUpRemaining = -1;
    private int loadedColour;
    private int loadedDamage;

    // plant food: staggered plasma balls
    private int pendingPlantFoodBalls = 0;
    private int plantFoodBallTimer = 0;
    private static final int PLANT_FOOD_BALL_DELAY = 5;
    private static final int PLANT_FOOD_BALL_DAMAGE = 600;

    public BowlingBulbAbility(int actionInterval, TriggerStrategy triggerStrategy,
                              int cyanReloadTicks, int blueReloadTicks, int orangeReloadTicks) {
        super(actionInterval, triggerStrategy);
        this.cyanReloadTicks = cyanReloadTicks;
        this.blueReloadTicks = blueReloadTicks;
        this.orangeReloadTicks = orangeReloadTicks;
    }

    @Override
    public void update(Plant owner, GameSession gameSession) {
        if (!hasCyan) {
            cyanTimer++;
            if (cyanTimer >= cyanReloadTicks) {
                hasCyan = true;
                cyanTimer = 0;
            }
        }

        if (!hasBlue) {
            blueTimer++;
            if (blueTimer >= blueReloadTicks) {
                hasBlue = true;
                blueTimer = 0;
            }
        }

        if (!hasOrange) {
            orangeTimer++;
            if (orangeTimer >= orangeReloadTicks) {
                hasOrange = true;
                orangeTimer = 0;
            }
        }

        // The wind-up started by execute() runs down here; the bulb leaves on the tick it ends.
        if (windUpRemaining > 0) {
            windUpRemaining--;
        } else if (windUpRemaining == 0) {
            windUpRemaining = -1;
            rollBulb(owner, gameSession);
        }

        updatePlantFoodBalls(owner, gameSession);

        super.update(owner, gameSession);
    }

    @Override
    public boolean canExecute(Plant owner, GameSession gameSession) {
        if (!hasCyan && !hasBlue && !hasOrange) return false;
        if (windUpRemaining >= 0) return false;   // already committed to a bulb

        return super.canExecute(owner, gameSession);
    }

    @Override
    public boolean isWindingUp() {
        return windUpRemaining >= 0;
    }

    // Which swing the plant should be playing: the colour it has loaded, zero-based, so the view can
    // append the number the same way it does for Kernel-pult.
    @Override
    public int actionVariant() {
        return Math.max(0, loadedColour - 1);
    }

    // Takes the bulb out of the magazine and starts the swing. The bulb itself rolls in rollBulb once
    // the wind-up ends, so the plant is seen to throw it rather than to react afterwards.
    @Override
    public void execute(Plant owner, GameSession gameSession) {
        if (hasCyan) {
            loadedColour = CYAN;
            loadedDamage = cyanDamage;
            hasCyan = false;
        } else if (hasBlue) {
            loadedColour = BLUE;
            loadedDamage = blueDamage;
            hasBlue = false;
        } else if (hasOrange) {
            loadedColour = ORANGE;
            loadedDamage = orangeDamage;
            hasOrange = false;
        } else {
            return;
        }
        windUpRemaining = WIND_UP_TICKS;
    }

    private void rollBulb(Plant owner, GameSession gameSession) {
        Projectile projectile = new Projectile(
                owner.getX() + 0.5,
                owner.getY(),
                ProjectileType.BOWLING_BULB,
                loadedDamage,
                1.0,
                0.0,
                owner,
                0.0,
                Element.NEUTRAL,
                Trajectory.DIRECT
        );

        // The bulb carries its own colour: it is drawn as the one the plant threw rather than as
        // whichever art the type happens to be mapped to, so a cyan swing does not roll out a blue bulb.
        projectile.setArtVariant(loadedColour);
        projectile.setBounceCount(3);
        gameSession.getMap().getRow(owner.getY()).addProjectile(projectile);
    }

    // Plant food: queues N large plasma balls, fired one at a time with a small delay.
    public void queuePlantFoodBalls(int count) {
        this.pendingPlantFoodBalls += count;
    }

    @Override
    public boolean isPlantFoodBusy() {
        return pendingPlantFoodBalls > 0;
    }

    private void updatePlantFoodBalls(Plant owner, GameSession gameSession) {
        if (pendingPlantFoodBalls <= 0) return;

        if (plantFoodBallTimer > 0) {
            plantFoodBallTimer--;
            return;
        }

        firePlantFoodBall(owner, gameSession);
        pendingPlantFoodBalls--;
        if (pendingPlantFoodBalls > 0) {
            plantFoodBallTimer = PLANT_FOOD_BALL_DELAY;
        }
    }

    private void firePlantFoodBall(Plant owner, GameSession gameSession) {
        Projectile projectile = new Projectile(
                owner.getX() + 0.5,
                owner.getY(),
                ProjectileType.PLASMA_BALL,
                PLANT_FOOD_BALL_DAMAGE,
                1.0,
                0.0,
                owner,
                0.0,
                Element.NEUTRAL,
                Trajectory.DIRECT
        );
        projectile.setSplashProperties(PLANT_FOOD_BALL_DAMAGE, 1.0, 1);
        gameSession.getMap().getRow(owner.getY()).addProjectile(projectile);
    }
}
