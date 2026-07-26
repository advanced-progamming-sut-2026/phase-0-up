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
    public void update(Plant owner, GameSession gameSession) {
        currentAliveTicks++;


        if (stageUpTicks != null && currentStage < stageUpTicks.length) {
            if (currentAliveTicks >= stageUpTicks[currentStage]) {
                currentStage++;
            }
        }

        super.update(owner, gameSession);
    }

    @Override
    public void execute(Plant owner, GameSession gameSession) {
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

            Sun sun = new Sun(targetX, owner.getY(), targetY, SunType.NORMAL, currentSunAmount, true, 100);

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
