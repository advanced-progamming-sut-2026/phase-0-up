package models.game.gamemodes;

import models.entities.zombies.Zombie;
import models.game.GameSession;
import models.map.Row;

// Default survival rules: clear every wave to win; lose if a zombie breaches the house on a row
// whose lawnmower is already spent. Every special level subclasses this and overrides only the
// win/lose hook it changes, so shared behaviour lives in one place.
public class StandardMode implements GameMode {

    @Override
    public void onStart(GameSession gameSession) {
    }

    @Override
    public void onTick(GameSession gameSession) {
    }

    @Override
    public boolean checkWin(GameSession gameSession) {
        int totalWaves = gameSession.getLevel().getWaveCount();
        return totalWaves > 0
                && gameSession.getCurrentWave() >= totalWaves
                && livingZombies(gameSession) == 0;
    }

    @Override
    public boolean checkLose(GameSession gameSession) {
        for (Row row : gameSession.getMap().getRows()) {
            boolean lawnmowerSpent = row.getLawnmower() == null || row.getLawnmower().isUsed();
            if (!lawnmowerSpent) {
                continue;
            }
            for (Zombie zombie : row.getZombies()) {
                if (!zombie.getHealth().isDead() && zombie.getX() <= 0) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean requiresSeedSelection(GameSession gameSession) {
        return true;
    }

    @Override
    public boolean isCommandAllowed(String commandType) {
        return true;
    }

    protected int livingZombies(GameSession gameSession) {
        int count = 0;
        for (Row row : gameSession.getMap().getRows()) {
            for (Zombie zombie : row.getZombies()) {
                if (!zombie.getHealth().isDead()) {
                    count++;
                }
            }
        }
        return count;
    }
}
