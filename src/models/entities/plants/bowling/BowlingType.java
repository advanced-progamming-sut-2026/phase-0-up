package models.entities.plants.bowling;

import models.entities.Entity;
import models.game.GameSession;

public class BowlingType extends Entity {
    public BowlingType(String name, int id, double x, int y) {
        super(name, id, x, y);
    }

    @Override
    public void update(GameSession gameSession) {
    }
}
