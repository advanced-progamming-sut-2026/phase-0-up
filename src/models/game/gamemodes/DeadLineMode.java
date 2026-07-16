package models.game.gamemodes;

import models.entities.zombies.Zombie;
import models.game.GameSession;
import models.map.Row;

// Special level: a vertical line sits at deadLineColumn. The instant any living zombie's X crosses
// it (zombies march from x=9 toward the house at x=0), the player loses. All other rules are standard.
public class DeadLineMode extends StandardMode {
    private final int deadLineColumn;

    public DeadLineMode(int deadLineColumn) {
        this.deadLineColumn = deadLineColumn;
    }

    public int getDeadLineColumn() {
        return deadLineColumn;
    }

    @Override
    public boolean checkLose(GameSession gameSession) {
        for (Row row : gameSession.getMap().getRows()) {
            for (Zombie zombie : row.getZombies()) {
                if (!zombie.getHealth().isDead() && zombie.getX() <= deadLineColumn) {
                    return true;
                }
            }
        }
        return super.checkLose(gameSession);
    }
}
