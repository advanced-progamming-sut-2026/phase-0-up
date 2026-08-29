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
        super("sun", onBoardX(x), (int) startY, expireTicks);
        this.x = onBoardX(x);
        this.currentY = startY;
        this.targetY = onBoardRow(targetY);
        this.type = type;
        this.amount = amount;
        this.falling = falling;
        this.fallSpeed = 0.05;
    }

    // Places the sun outright. Same reason as Projectile.placeAt: on a networked client the board is
    // mirrored from the server's snapshot, so a falling sun's height is told to it rather than
    // computed -- and currentY, not y, is what the view draws it at while it is still in the air.
    //
    // ## targetY is told to us too, and it has to be
    //
    // A sun is COLLECTED by naming the tile the model files it under, and for one still in the air that
    // tile is the one it is heading for -- floor(targetY) -- not the one it is passing over. A mirrored
    // sun built with targetY = wherever it happened to be when this client first saw it therefore
    // addresses a row the server does not have it in, and "collect sun" comes back "no sun there" for
    // the whole of its fall. The view needs the real value as well: it draws a resting sun at its
    // fractional target height.
    //
    // (int), not Math.round: this has to file the sun in exactly the row onReachGround would, and that
    // one floors. Rounding put every sky sun whose target fell in the lower half of its row -- a bit
    // under half of them -- one lane below where the server had it, and those could never be collected
    // either.
    public void placeAt(double x, double currentY, double targetY, boolean falling) {
        this.x = onBoardX(x);
        this.currentY = currentY;
        this.targetY = onBoardRow(targetY);
        this.falling = falling;
        if (!falling) {
            this.y = (int) currentY;
        }
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

    // A sun that comes to rest off the board cannot be collected, so one is never built off the board.
    //
    // `collect sun` names a TILE, and CollectSunCommand refuses a negative or out-of-range coordinate
    // outright. A sun at x = -0.25 therefore sat on the lawn -- drawn, clickable, worth its full value
    // -- and answered every click with "Invalid coordinates (-1, 0)". Three separate abilities scatter
    // suns with a random offset around their owner, and two of them have now had this bug: the first
    // was fixed in place and the second was found in play months later (Gold Bloom, in a scoring run).
    // Clamping here is what makes the third one impossible rather than merely unlikely.
    //
    // Only the resting position is clamped. currentY is deliberately left alone: a sky sun BEGINS
    // above the board, several rows into negative y, and that fall is the whole point of it.
    private static double onBoardX(double x) {
        // Just inside the last column, so Math.floor still names a column that exists.
        return Math.max(0d, Math.min(x, utils.Constants.BOARD_COLS - 0.001d));
    }

    private static double onBoardRow(double y) {
        return Math.max(0d, Math.min(y, utils.Constants.BOARD_ROWS - 0.001d));
    }

    // The tile this sun is COLLECTED by naming, and the only place that rule is written down.
    //
    // "collect sun -l (x, y)" addresses a tile, so the view has to name the same one the model will
    // look in. That rule used to exist twice -- in SunSystem.findSunAt and again in
    // LawnInputProcessor.tileOf -- and two copies of a rule in two layers is how the networked build
    // ended up with suns the plant player could see, could click, and could not pick up. Neither copy
    // was wrong on its own; they were reading different fields on a mirrored sun.
    //
    // A sun in the air belongs to the tile it is FALLING TOWARDS, not the one it is passing over: it is
    // drawn several cells above its target, and claiming it for the tile underneath would mean clicking
    // empty sky to catch one.
    public int tileColumn() {
        return (int) Math.floor(x);
    }

    // Clamped on the way out as well as on the way in. targetY is already on the board, but `y` is set
    // from the START height -- which is the resting height for a plant-made sun and several rows above
    // the board for a sky one -- so this is the only place that can promise the answer names a row that
    // exists.
    public int tileRow() {
        return (int) onBoardRow(falling ? Math.floor(targetY) : y);
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
