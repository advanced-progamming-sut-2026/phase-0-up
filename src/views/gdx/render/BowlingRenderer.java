package views.gdx.render;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import models.entities.plants.bowling.BowlingKind;
import models.entities.plants.bowling.BowlingType;
import models.game.GameSession;
import models.game.gamemodes.WallnutBowlingMode;
import views.gdx.map.LawnGeometry;
import views.gdx.sprite.ClipMap;
import views.gdx.sprite.EntitySprite;
import views.gdx.sprite.SpriteRegistry;

// The nuts rolling down Wall-nut Bowling's lanes.
//
// Three distinct sprites, because the three kinds behave completely differently on contact -- a bowling
// nut ricochets, an Explode-o-Nut detonates, a giant one ploughs straight through -- and a player who
// cannot tell which one is halfway down lane 3 cannot plan the next throw. All three ship: WALLNUT and
// EXPLODEONUT are the plants themselves, and PRIMAL_WALLNUT is the game's own bigger, tougher-looking
// wall-nut, which is exactly what "giant" has to read as.
//
// A nut is NOT a plant standing in a tile: it carries its own continuous (px, py) and travels diagonally
// after a ricochet, so it is drawn at that position and spun, not stood on a foot line.
public final class BowlingRenderer {

    private static final String NUT_PLAIN = "WALLNUT";
    private static final String NUT_EXPLODE = "EXPLODEONUT";
    private static final String NUT_GIANT = "PRIMAL_WALLNUT";

    // How wide each kind is drawn, in cells. The giant nut is the one rule the player has to READ off
    // the board rather than remember, so it is the one that is visibly bigger.
    private static final float WIDTH_CELLS = 0.9f;
    private static final float GIANT_WIDTH_CELLS = 1.35f;

    // Degrees of spin per cell travelled.
    //
    // Not decorative: a nut that slides rather than rolls reads as a projectile, and a projectile is a
    // thing you shoot rather than a thing you BOWL. Derived rather than picked -- a nut about 0.9 cells
    // across covers pi * 0.9 cells in one full turn, so one cell of travel is roughly a third of one.
    private static final float SPIN_DEGREES_PER_CELL = 127f;

    private final SpriteRegistry sprites;
    private final LawnGeometry lawn;
    private final AnimationClocks clocks;
    private final views.gdx.bridge.EntityInterpolator interpolator;

    public BowlingRenderer(SpriteRegistry sprites, LawnGeometry lawn, AnimationClocks clocks,
                           views.gdx.bridge.EntityInterpolator interpolator) {
        this.sprites = sprites;
        this.lawn = lawn;
        this.clocks = clocks;
        this.interpolator = interpolator;
    }

    // Drawn per lane, with the zombies it is about to hit, so a nut in a nearer row passes in front of
    // one behind it. A nut's lane is its ROUNDED py -- the same number the mode's collision check uses,
    // so what the player sees it about to hit is what it will hit.
    public void drawRow(Batch batch, GameSession session, int row, float delta, float alpha) {
        WallnutBowlingMode mode = modeOf(session);
        if (mode == null) {
            return;
        }
        for (BowlingType ball : mode.getBalls()) {
            if (ball.getRow() == row) {
                drawBall(batch, ball, delta, alpha);
            }
        }
    }

    private void drawBall(Batch batch, BowlingType ball, float delta, float alpha) {
        EntitySprite sprite = sprites.get(spriteFor(ball.getKind()));
        if (sprite == null || !sprite.isReady()) {
            return;
        }
        String clip = views.gdx.sprite.PlantStages.restingClip(sprite);
        Rectangle bounds = sprite.bounds(clip);
        if (bounds == null || bounds.width <= 0f) {
            return;
        }

        // INTERPOLATED and continuous. The model steps half a column per tick at 10 Hz, so drawing
        // straight from it is a nut jumping a third of a tile six times a second; and py is fractional
        // while a ricocheted nut crosses between lanes, so snapping it to a row would turn the diagonal
        // into a staircase.
        float px = interpolator.x(ball, ball.getPx(), alpha);
        float py = interpolator.lane(ball, ball.getRow(), alpha);
        float centreX = lawn.worldX(px);
        float centreY = lawn.worldY(py) + lawn.cellHeight() * 0.5f;

        float widthCells = ball.getKind() == BowlingKind.GIANT ? GIANT_WIDTH_CELLS : WIDTH_CELLS;
        float scale = SpritePlacer.toSpriteSpace(widthCells * lawn.cellWidth()) / bounds.width;
        // Spun off the interpolated distance too, or the roll stutters even though the position does not.
        float spin = -px * SPIN_DEGREES_PER_CELL;

        // Rotating the batch's transform rather than the sprite: a PAM has no rotation of its own, and
        // its parts would have to be spun individually. Same trick CursorRenderer uses to scale one.
        Matrix4 previous = batch.getTransformMatrix().cpy();
        batch.setTransformMatrix(new Matrix4(previous)
                .translate(SpritePlacer.toSpriteSpace(centreX), SpritePlacer.toSpriteSpace(centreY), 0f)
                .rotate(0f, 0f, 1f, spin)
                .scale(scale, scale, 1f));
        // Drawn about its own centre so the spin is a roll and not an orbit. The y term is the same
        // y-down correction as everywhere else: the art hangs below the .PAM origin.
        float stateTime = ClipMap.sample(sprite, clip, clocks.advance(ball, clip, delta));
        sprite.draw(batch, clip, stateTime, 0f, bounds.y + bounds.height / 2f, true);
        batch.setTransformMatrix(previous);
    }

    // The sprite for one kind of nut. Public because the cursor draws the held nut and the conveyor
    // draws the waiting ones, and all three have to agree.
    public static String spriteFor(BowlingKind kind) {
        if (kind == null) {
            return NUT_PLAIN;
        }
        return switch (kind) {
            case EXPLODE -> NUT_EXPLODE;
            case GIANT -> NUT_GIANT;
            default -> NUT_PLAIN;
        };
    }

    // The token `bowl -t <token>` takes. Exactly what WallnutBowlingMode.parseKind accepts, so the
    // command this produces cannot be one the mode rejects.
    public static String tokenFor(BowlingKind kind) {
        return kind == null ? "bowling" : kind.name().toLowerCase(java.util.Locale.ROOT);
    }

    public static BowlingKind kindFor(String token) {
        if (token == null) {
            return null;
        }
        for (BowlingKind kind : BowlingKind.values()) {
            if (tokenFor(kind).equals(token.trim().toLowerCase(java.util.Locale.ROOT))) {
                return kind;
            }
        }
        return null;
    }

    // How a player-facing label reads. "Explode-o-Nut", not "EXPLODE".
    public static String labelFor(BowlingKind kind) {
        if (kind == null) {
            return "";
        }
        return switch (kind) {
            case EXPLODE -> "Explode-o-Nut";
            case GIANT -> "Giant Wall-nut";
            default -> "Wall-nut";
        };
    }

    // The plant whose SEED PACKET stands for this nut on the belt.
    //
    // Not labelFor: that is what the nut is called, this is which shipped packet portrait draws it, and
    // for the giant nut the two differ -- the dump has no "Giant Wall-nut" packet, and Primal Wall-nut's
    // is the bigger, tougher-looking one that the giant nut IS.
    public static String packetPlantFor(BowlingKind kind) {
        if (kind == null) {
            return "Wall-nut";
        }
        return switch (kind) {
            case EXPLODE -> "Explode-o-Nut";
            case GIANT -> "Primal Wall-nut";
            default -> "Wall-nut";
        };
    }

    public static WallnutBowlingMode modeOf(GameSession session) {
        if (session != null && session.getMode() instanceof WallnutBowlingMode bowling) {
            return bowling;
        }
        return null;
    }
}
