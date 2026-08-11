package models.entities.plants.abilities;

import models.entities.collectibles.Sun;
import models.entities.collectibles.SunType;
import models.entities.plants.Plant;
import models.entities.plants.abilities.triggers.TriggerStrategy;
import models.game.GameSession;

import java.util.Random;

public class ProduceSunAbility extends PlantAbility implements Growable {
    private int[] sunAmountsByStage;
    private int[] stageUpTicks;

    private int currentStage;
    private int currentAliveTicks;

    private Random random;
    private double doubleSunChance;
    private int spawnCount;

    // Ticks the plant spends blooming before the sun actually appears, exactly like a shooter's
    // wind-up: the sun is created when this expires, so the produce animation runs THROUGH it and the
    // sun pops out on the animation's release rather than materialising before it has started.
    // The production rate is untouched -- PlantAbility resets cooldownTimer to actionInterval on
    // execute(), so only the moment within the cycle moves.
    private static final int WIND_UP_TICKS = 8;   // 0.8s at 10 ticks/sec
    private int windUpRemaining = -1;             // -1 = not winding up

    public ProduceSunAbility(int actionIntervalTicks, TriggerStrategy triggerStrategy,
                             int[] sunAmountsByStage, int[] stageUpTicks,
                             double doubleSunChance, int spawnCount) {
        super(actionIntervalTicks, triggerStrategy);
        this.sunAmountsByStage = sunAmountsByStage;
        this.stageUpTicks = stageUpTicks;
        this.doubleSunChance = doubleSunChance;
        this.spawnCount = spawnCount;

        this.random = new Random();
        this.currentStage = 0;
        this.currentAliveTicks = 0;
    }


    @Override
    public boolean isWindingUp() {
        return windUpRemaining >= 0;
    }

    @Override
    public boolean canExecute(Plant owner, GameSession gameSession) {
        if (windUpRemaining >= 0) {
            return false;   // already blooming; do not stack a second production on top
        }
        return super.canExecute(owner, gameSession);
    }

    @Override
    public void update(Plant owner, GameSession gameSession) {
        currentAliveTicks++;


        if (stageUpTicks != null && currentStage < stageUpTicks.length) {
            if (currentAliveTicks >= stageUpTicks[currentStage]) {
                currentStage++;
            }
        }

        // The bloom started by execute() runs down here, and the sun appears on the tick it ends.
        if (windUpRemaining > 0) {
            windUpRemaining--;
        } else if (windUpRemaining == 0) {
            windUpRemaining = -1;
            releaseSuns(owner, gameSession);
        }

        super.update(owner, gameSession);
    }

    // Begins the bloom. The suns themselves are created in releaseSuns once the wind-up elapses.
    @Override
    public void execute(Plant owner, GameSession gameSession) {
        windUpRemaining = WIND_UP_TICKS;
    }

    // The bloom has finished: the sun appears now.
    private void releaseSuns(Plant owner, GameSession gameSession) {
        int count = spawnCount;

        int currentSunAmount = sunAmountsByStage[currentStage];

        if (doubleSunChance > 0 && random.nextDouble() < doubleSunChance) {
            count *= 2;
        }

        for (int i = 0; i < count; i++) {
            // Jitter only forward within the plant's own column: a negative offset used to push the
            // sun into the previous column, where collectSun (which floors x) could never find it, so
            // that sun never reached the wallet. Keeping it in-column makes it collectable at the
            // plant's tile.
            double targetX = owner.getX() + random.nextDouble() * 0.4;

            double offsetY = random.nextDouble() * 0.6;
            double targetY = owner.getY() + offsetY;

            // NOT falling. "Falling" means descending from the sky, and this sun never was in the sky
            // -- the plant made it, right here. Flagging it as falling made it identical to a sky sun
            // to everything downstream: it was drawn plummeting from above the board, and the produce
            // animation was skipped entirely because that code path only runs for suns that are NOT
            // falling. It settles beside the plant instead, on its own tile where it can be collected.
            Sun sun = new Sun(targetX, targetY, targetY, SunType.NORMAL, currentSunAmount, false, 100);

            gameSession.addSun(sun);
        }

        // Announce the production so the player knows to collect it (project.md's standard line).
        gameSession.reportEvent("plant " + owner.getName() + " produced a sun at ("
                + (int) owner.getX() + ", " + owner.getY() + ")");
    }

    @Override
    public void growToMaxStage() {
        this.currentStage = sunAmountsByStage.length - 1;
    }

    // Upgrade (DOUBLE_SUN_CHANCE): sets the probability that a production run yields double sun.
    public void setDoubleSunChance(double chance) {
        this.doubleSunChance = chance;
    }

    // Upgrade (GROW_TIME_REDUCTION): shortens every stage-up threshold, capped at 0.
    public void reduceStageUpTicks(int ticks) {
        if (stageUpTicks == null) {
            return;
        }
        for (int i = 0; i < stageUpTicks.length; i++) {
            stageUpTicks[i] = Math.max(0, stageUpTicks[i] - ticks);
        }
    }

    // Upgrade (SUN_AMOUNT_BUFF / SUN_DROP_INCREMENT): adds to the sun produced at every stage.
    public void increaseSunAmounts(int amount) {
        if (sunAmountsByStage == null) {
            return;
        }
        for (int i = 0; i < sunAmountsByStage.length; i++) {
            sunAmountsByStage[i] += amount;
        }
    }
}
