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

    // Jitter stays inside the plant's OWN column. A plant sits at `column + 0.5`, and the spread used
    // to be +/-0.75 around that -- so a burst from column 0 could land at x = -0.25, and `collect sun`
    // floors x to name a tile, which made that tile column -1. CollectSunCommand refuses a negative
    // coordinate outright, so the sun sat on the lawn, visible, worth 375, and impossible to pick up.
    //
    // ProduceSunAbility hit this exact bug and fixed it for itself; this is the same fix, and Sun's
    // constructor now clamps as well so the next sun spawner cannot reintroduce it a third time.
    private static final double COLUMN_JITTER = 0.8;   // +/-0.4 around the plant, inside its tile
    private static final double DROP_JITTER = 0.8;

    @Override
    public void execute(Plant owner, GameSession gameSession) {
        for (int i = 0; i < spawnCount; i++) {
            double targetX = owner.getX() + (random.nextDouble() - 0.5) * COLUMN_JITTER;
            double targetY = owner.getY() + random.nextDouble() * DROP_JITTER;

            gameSession.addSun(new Sun(targetX, owner.getY(), targetY, SunType.NORMAL,
                    sunAmount, true, 100));
        }

        // Outside the loop. Gold Bloom spawns one sun so it never showed, but marking the ability spent
        // and killing the plant once per sun is a statement about the PLANT, not about each sun.
        this.hasExecuted = true;
        if (owner.getHealth() != null) {
            owner.getHealth().takeDamage(owner.getHealth().getMaxHp());
        }
    }
}
