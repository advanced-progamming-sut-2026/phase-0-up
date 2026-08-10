package views.gdx.render;

import com.badlogic.gdx.graphics.g2d.Batch;
import models.game.EnvironmentType;
import models.map.Lawnmower;
import views.gdx.bridge.EntityInterpolator;
import views.gdx.map.LawnGeometry;
import views.gdx.sprite.ClipMap;
import views.gdx.sprite.EntitySprite;
import views.gdx.sprite.SpriteRegistry;

// Draws the mower parked at the left end of a lane.
//
// The model already gives the mower a continuous position and drives it up the row when it triggers
// (Lawnmower.update adds LAWNMOWER_SPEED per tick), so the "continuous lawnmower movement" listed as
// an aesthetic bonus in the spec needs nothing extra here -- just draw where the model says it is.
// Once it has run off the board it is spent and stops being drawn.
public final class LawnmowerRenderer {

    private final SpriteRegistry sprites;
    private final LawnGeometry lawn;
    private final EntityInterpolator interpolator;
    private final String spriteName;

    public LawnmowerRenderer(SpriteRegistry sprites, LawnGeometry lawn,
                             EntityInterpolator interpolator, EnvironmentType environment) {
        this.sprites = sprites;
        this.lawn = lawn;
        this.interpolator = interpolator;
        this.spriteName = mowerFor(environment);
    }

    // Each world has its own mower: MOWER_EGYPT, MOWER_ICEAGE, MOWER_BEACH, MOWER_DARK.
    private static String mowerFor(EnvironmentType environment) {
        if (environment == null) {
            return "MOWER_EGYPT";
        }
        return switch (environment) {
            case ANCIENT_EGYPT -> "MOWER_EGYPT";
            case FROSTBITE_CAVES -> "MOWER_ICEAGE";
            case BIG_WAVE_BEACH -> "MOWER_BEACH";
            case DARK_AGES -> "MOWER_DARK";
        };
    }

    public void draw(Batch batch, Lawnmower mower, int row, float stateTime, float alpha) {
        if (mower == null || mower.isUsed()) {
            return;   // spent: it has driven off the board and is not coming back
        }
        EntitySprite sprite = sprites.get(spriteName);
        String clip = ClipMap.firstAvailable(sprite, mower.isActiveNow() ? "walk" : ClipMap.IDLE);

        // Parked mowers sit just off the left edge of column 0; a running one advances up the row.
        float modelX = (float) mower.getPositionX();
        float x = lawn.worldX(interpolator.x(mower, modelX, alpha)) - lawn.cellWidth() * 0.55f;
        float footY = lawn.worldY(row) + lawn.cellHeight() * SpritePlacer.FOOT_INSET;

        SpritePlacer.drawStanding(batch, sprite, clip, stateTime, x, footY, true, null);
    }
}
