package models.entities.collectibles;

import models.game.GameSession;

public class Sun extends Collectible {
    private SunType type;
    private int amount;

    private boolean falling;
    private double currentY;
    private double targetY;
    private double fallSpeed;

    public Sun(double x, double startY, double targetY, SunType type, int amount, boolean falling, int expireTicks) {
        super("sun", x, (int) startY, expireTicks);
        this.currentY = startY;
        this.targetY = targetY;
        this.type = type;
        this.amount = amount;
        this.falling = falling;
        this.fallSpeed = 0.05;
    }

    public void onReachGround() {
        this.falling = false;
        this.y = (int) currentY;
    }

    @Override
    protected void applyEffect(GameSession gameSession) {
        gameSession.increaseSunAmount(this.amount);
    }

    @Override
    public void update(GameSession gameSession) {
        // The expiry clock does not start until the sun is actually sitting on the lawn.
        //
        // Counting from the moment it spawns charges a sky sun for the five seconds it spends falling,
        // which is half its life: SKY_SUN_GROUND_EXPIRE_TICKS says 10 seconds on the GROUND, but the
        // player only ever got about five to reach it. A sun still in mid-air is not an uncollected sun
        // going stale, it is a sun on its way. Plant-made suns are not falling to begin with, so their
        // window is unchanged.
        if (!falling) {
            super.update(gameSession);
        }

        if (isRemovable()) {
            return;
        }

        if (falling) {
            if (currentY < targetY) {
                currentY += fallSpeed;
                this.y = (int) currentY;
            } else {
                onReachGround();
            }
        }
    }

    // --- Getters ---
    public SunType getType() { return type; }
    public int getAmount() { return amount; }
    public boolean isFalling() { return falling; }

    // The exact height of a sun mid-fall. Entity.getY() rounds to an int row, which is all the terminal
    // view ever needed, but a sun drawn on a rounded row snaps down the screen a whole cell at a time.
    // The precise value already exists here -- it just had no way out.
    public double getCurrentY() { return currentY; }

    public double getTargetY() {
        return targetY;
    }
}
