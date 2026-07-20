package models.map.Terrains;

import models.entities.collectibles.Sun;
import models.entities.collectibles.SunType;
import models.entities.projectiles.Element;
import models.game.GameSession;
import models.map.Cell;

import java.util.Random;

// A Dark Ages headstone: 700 HP, blocks shots like an Egypt grave, and releases its contents when
// broken. SUNNY drops a sun, FOODY a plant food, PLAIN nothing.
public class GraveInDarkAgesTerrain extends GraveTerrain {
    private final GravesInDarkAgesTypes type;
    private final GameSession gameSession;
    private final Cell cell;
    private final Random random = new Random();

    public GraveInDarkAgesTerrain(GravesInDarkAgesTypes type, GameSession gameSession, Cell cell) {
        this.type = type;
        this.symbol = '?';
        this.hp = 700;
        this.gameSession = gameSession;
        this.cell = cell;
    }

    public boolean hasLoot() {
        return type == GravesInDarkAgesTypes.SUNNY || type == GravesInDarkAgesTypes.FOODY;
    }

    public GravesInDarkAgesTypes getType() {
        return type;
    }

    // The real damage path: projectiles call takeDamage, not getShot. Poison does not hurt graves;
    // anything else chips the 700 HP, and breaking it spills whatever it held.
    @Override
    public void takeDamage(int damage, Element element) {
        if (isDead || element == Element.POISON) {
            return;
        }
        hp -= damage;
        if (hp <= 0) {
            hp = 0;
            isDead = true;
            dropCollectibles();
        }
    }

    @Override
    public void getShot(int damage) {
        takeDamage(damage, Element.NEUTRAL);
    }

    private void dropCollectibles() {
        if (type == GravesInDarkAgesTypes.SUNNY) {
            double targetX = cell.getX() + (random.nextDouble() - 0.5);
            double targetY = cell.getY() + random.nextDouble() * 0.6;
            gameSession.addSun(new Sun(targetX, cell.getY(), targetY, SunType.NORMAL, 50, true, 100));
            gameSession.reportEvent("A grave crumbles at (" + (int) cell.getX() + ", " + cell.getY()
                    + ") and releases a sun.");
        } else if (type == GravesInDarkAgesTypes.FOODY) {
            gameSession.increasePlantFoodCount(1);
            gameSession.reportEvent("A grave crumbles at (" + (int) cell.getX() + ", " + cell.getY()
                    + ") and releases a plant food.");
        }
    }
}
