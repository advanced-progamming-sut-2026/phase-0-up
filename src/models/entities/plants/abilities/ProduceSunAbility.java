package models.entities.plants.abilities;

import models.entities.collectibles.Sun;
import models.entities.collectibles.SunType;
import models.entities.plants.Plant;
import models.game.GameSession;

import java.util.Random;

public class ProduceSunAbility extends PlantAbility {
    private int[] sunAmountsByStage;
    private int[] stageUpTicks;

    private int currentStage;
    private int currentAliveTicks;

    private Random random;
    private double doubleSunChance;
    private int spawnCount;

    public ProduceSunAbility(int actionIntervalTicks, int[] sunAmountsByStage, int[] stageUpTicks,
                             double doubleSunChance, int spawnCount) {
        super(actionIntervalTicks);
        this.sunAmountsByStage = sunAmountsByStage;
        this.stageUpTicks = stageUpTicks;
        this.doubleSunChance = doubleSunChance;
        this.spawnCount = spawnCount;

        this.random = new Random();
        this.currentStage = 0;
        this.currentAliveTicks = 0;
    }

    @Override
    public boolean canExecute(Plant owner, GameSession gameSession) {
        return true;
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
            double offsetX = random.nextDouble() - 0.5;
            double targetX = owner.getX() + offsetX;

            double offsetY = random.nextDouble() * 0.6;
            double targetY = owner.getY() + offsetY;

            Sun sun = new Sun(targetX, owner.getY(), targetY, SunType.NORMAL, currentSunAmount, true, 100);

            gameSession.addSun(sun);
        }
    }
}
