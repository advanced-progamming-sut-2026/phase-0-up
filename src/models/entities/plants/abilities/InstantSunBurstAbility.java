package models.entities.plants.abilities;

import models.entities.collectibles.Sun;
import models.entities.collectibles.SunType;
import models.entities.plants.Plant;
import models.game.GameSession;

import java.util.Random;

public class InstantSunBurstAbility extends PlantAbility{
    private int sunAmount;
    private int spawnCount;
    private Random random;
    private boolean hasExecuted;

    public InstantSunBurstAbility(int sunAmount, int spawnCount) {
        super(0, null);
        this.sunAmount = sunAmount;
        this.spawnCount = spawnCount;
        this.random = new Random();
        this.hasExecuted = false;
    }

    @Override
    public boolean canExecute(Plant owner, GameSession gameSession) {
        return !hasExecuted;
    }

    // Upgrade (SUN_AMOUNT_BUFF): increases the one-shot sun payout (Gold Bloom).
    public void increaseSunAmount(int amount) {
        this.sunAmount += amount;
    }

    @Override
    public void execute(Plant owner, GameSession gameSession) {
        for (int i = 0; i < spawnCount; i++) {
            double offsetX = (random.nextDouble() - 0.5) * 1.5;
            double targetX = owner.getX() + offsetX;

            double offsetY = random.nextDouble() * 0.8;
            double targetY = owner.getY() + offsetY;

            Sun sun = new Sun(targetX, owner.getY(), targetY, SunType.NORMAL, sunAmount, true, 100);

            gameSession.addSun(sun);

            this.hasExecuted = true;

            if (owner.getHealth() != null) {
                owner.getHealth().takeDamage(owner.getHealth().getMaxHp());
            }
        }
    }
}
