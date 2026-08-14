package views.gdx.ui;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.utils.Align;
import views.gdx.sprite.ClipMap;
import views.gdx.sprite.EntitySprite;

// The difficulty dial: a jalapeno that gets angrier as the setting climbs.
//
// DIFFICULTY_METER.PAM ships exactly five clips -- animation, animation2 .. animation5 -- and they are
// five stages of the same face, from a calm green pepper to a furious flaming one. That is not a
// coincidence to be worked around; it is a five-position gauge, and the game's difficulty has five
// positions. So the stage IS the clip index.
//
// Each clip is authored at its own size (the calm one is about 105x176 PAM units, the flaming one
// nearly 600x640), so a single scale factor would make stage 1 tiny and stage 4 overflow. Every clip is
// fitted to the actor's box against its own bounds instead.
public final class DifficultyMeter extends Actor {

    public static final String SPRITE = "DIFFICULTY_METER";

    // Clip per stage, lowest first. Named rather than derived so a missing one is obvious here rather
    // than as a blank widget.
    private static final String[] STAGES = {
        "animation", "animation2", "animation3", "animation4", "animation5"
    };

    // How much of the actor's height the PEPPER occupies. The rest is headroom for the flame, which at
    // the top stages reaches far past the pepper itself.
    private static final float PEPPER_FRACTION = 0.62f;

    private final EntitySprite sprite;

    // Every stage is drawn at ONE scale, taken from the calm clip.
    //
    // Fitting each clip to its own bounds was wrong, and looked it: the pepper is the same size in all
    // five, but the bounds are not, because the angry stages add flame and glow that extend well past
    // the body. Scaling each clip to its own extent therefore shrank the pepper as the difficulty rose
    // -- the exact opposite of what a gauge should do.
    //
    // The offset comes from the same reference clip too, so the body stays put and the flame grows
    // around it rather than shoving it off-centre.
    private final Rectangle reference;

    private int stage;
    private float stateTime;

    public DifficultyMeter(EntitySprite sprite, int difficultyLevel) {
        this.sprite = sprite;
        this.stage = clampStage(difficultyLevel);
        this.reference = sprite == null ? null : sprite.bounds(STAGES[0]);
        setOrigin(Align.center);
    }

    // difficultyLevel is 1-based, matching the model and the command. Anything outside the range is
    // pinned rather than rejected: a profile carrying a stale value should still draw something.
    private static int clampStage(int difficultyLevel) {
        return Math.max(0, Math.min(STAGES.length - 1, difficultyLevel - 1));
    }

    // Swaps the face, and reacts. The pop is the point: without it a difficulty change two clicks away
    // looks identical to one click away, because the two faces next to each other are similar.
    public void setDifficultyLevel(int difficultyLevel) {
        int next = clampStage(difficultyLevel);
        if (next == stage) {
            return;
        }
        stage = next;
        stateTime = 0f;   // start the new clip from its first frame, not mid-way through the old one
        clearActions();
        setScale(1f);
        addAction(Actions.sequence(
                Actions.scaleTo(1.18f, 1.18f, 0.09f, Interpolation.swingOut),
                Actions.scaleTo(1f, 1f, 0.16f, Interpolation.sine)));
    }

    public static int stageCount() {
        return STAGES.length;
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        stateTime += delta;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        if (sprite == null || !sprite.isReady()) {
            return;
        }
        if (reference == null || reference.height <= 0f) {
            return;
        }
        // From the reference clip only -- never from the clip being drawn.
        float fit = getHeight() * PEPPER_FRACTION / reference.height;
        // The actor's own scale is the pop. They multiply.
        float scale = fit * getScaleX();

        Matrix4 previous = batch.getTransformMatrix().cpy();
        batch.setTransformMatrix(new Matrix4(previous)
                .translate(getX() + getWidth() / 2f, getY() + getHeight() / 2f, 0f)
                .scale(scale, scale, 1f));
        // Same y-down correction as PlantIcon and SpritePlacer: libPVZ reports bounds in the .PAM's own
        // Flash-style coordinates, where the art hangs BELOW the origin.
        sprite.draw(batch, clip(), ClipMap.sample(sprite, clip(), stateTime),
                0f, reference.y + reference.height / 2f, true);
        batch.setTransformMatrix(previous);
    }

    // Falls back to the calm face rather than drawing nothing, so a renamed clip costs the gauge its
    // range and not its existence.
    private String clip() {
        String wanted = STAGES[stage];
        return sprite.hasClip(wanted) ? wanted : ClipMap.firstAvailable(sprite, STAGES[0]);
    }
}
