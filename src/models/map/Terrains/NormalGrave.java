package models.map.Terrains;

import models.entities.projectiles.Element;
import models.game.GameSession;
import models.map.Cell;

public class NormalGrave extends GraveTerrain{
    private GameSession gameSession;
    private Cell cell;

    public NormalGrave(GameSession gameSession, Cell cell) {
        this.hp = 700;
        this.isDead = false;
        this.plantable = false;
        this.gameSession = gameSession;
        this.cell = cell;
    }


    // Projectiles call takeDamage (getShot is the older, direct entry point), so the crumble
    // announcement lives here and getShot routes through it -- otherwise a grave shot down by a pea
    // would break in silence while one broken any other way reported itself.
    @Override
    public void takeDamage(int damage, Element element) {
        if (isDead) {
            return;
        }
        super.takeDamage(damage, element);
        if (isDead) {
            announceCrumble();
        }
    }

    @Override
    public void getShot(int damage) {
        takeDamage(damage, Element.NEUTRAL);
    }

    private void announceCrumble() {
        if (gameSession == null || cell == null) {
            return;
        }
        gameSession.reportEvent("The grave at (" + (int) cell.getX() + ", " + cell.getY()
                + ") crumbles to dust. That tile is free again!");
    }
}
