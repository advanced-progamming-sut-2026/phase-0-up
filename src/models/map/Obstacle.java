package models.map;

import models.entities.projectiles.Element;
import models.game.GameSession;

public abstract class Obstacle {
    protected int hp;
    protected double x;
    protected int y;
    protected boolean isDestroyed = false;
    // Set by whoever spawns the obstacle (a zombie ability), so the obstacle can report its own
    // destruction to the view through the session's event queue rather than printing.
    protected GameSession gameSession;

    public Obstacle(int hp, double x, int y) {
        this.hp = hp;
        this.x = x;
        this.y = y;
    }

    public Obstacle(int hp, double x, int y, GameSession gameSession) {
        this(hp, x, y);
        this.gameSession = gameSession;
    }

    // Routes a narrative line to the view via the session, when one is attached.
    protected void report(String message) {
        if (gameSession != null) {
            gameSession.reportEvent(message);
        }
    }

    // متد دمیج خوردن که هر مانع باید به روش خودش پیاده‌سازی کند
    public abstract void takeDamage(int damage, Element element);

    public double getX() { return x; }
    public int getY() { return y; }
    public int getHp() { return hp; }
    public boolean isDestroyed() { return isDestroyed; }
}