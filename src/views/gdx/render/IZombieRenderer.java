package views.gdx.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import models.game.GameSession;
import models.game.gamemodes.IZombieMode;
import views.gdx.core.Assets;
import views.gdx.map.LawnGeometry;

// The five brains I, Zombie is played for.
//
// One per lane, standing where the lawn mower would be -- which is not a coincidence: the mode removes
// the mowers in onStart and the brain takes the slot, so "the thing at the end of the lane" means the
// same thing to the player in both games. Eating one is how that lane is won, and until now the only
// sign a lane had been cleared was a line of prose in the toast corner.
//
// A still image rather than an animation. BRAIN_EFFECT is a PAM, but its single clip is the BURST that
// plays when a brain is eaten -- a 460x352 splash, not a brain sitting on the ground -- so a region is
// drawn directly instead.
//
// The ZombieTreadmillBrain atlas holds four pink shapes and only ONE of them is a brain: a glow, a
// cloud, the brain, and a splatter particle. `BRAIN_EFFECT_49X59` is the last of those, and drawn here
// it put a magenta smudge at the end of every lane. Names in this dump describe the EFFECT a part
// belongs to, not what the part is a picture of.
public final class IZombieRenderer {

    // The brain itself: 112x82 authored, folds and all.
    private static final String BRAIN =
            "IMAGE_ZOMBIE_POWER_BRAIN_PROJECTILE_POWER_BRAIN_PROJECTILE_112X82";

    private static final float WIDTH_CELLS = 0.78f;
    private static final float ASPECT = 82f / 112f;
    // How far left of column 0 it sits: the same side of the lawn edge the mower parks on.
    private static final float LEFT_OF_LAWN_CELLS = 0.55f;
    private static final float FOOT_LIFT = 0.12f;

    // An eaten brain is not removed, it is left as a stain. A lane that simply emptied would read as a
    // brain that was never there, and "four of five eaten" is the score.
    private static final Color EATEN = new Color(0.35f, 0.30f, 0.34f, 0.55f);

    private final Assets assets;
    private final LawnGeometry lawn;

    private TextureRegion brain;
    private boolean looked;

    public IZombieRenderer(Assets assets, LawnGeometry lawn) {
        this.assets = assets;
        this.lawn = lawn;
    }

    public void drawRow(Batch batch, GameSession session, int row) {
        IZombieMode mode = modeOf(session);
        if (mode == null || row >= mode.brainsTotal()) {
            return;
        }
        TextureRegion region = brain();
        if (region == null) {
            return;
        }
        float width = lawn.cellWidth() * WIDTH_CELLS;
        float height = width * ASPECT;
        float x = lawn.worldX(0) - lawn.cellWidth() * LEFT_OF_LAWN_CELLS - width * 0.5f;
        float y = lawn.worldY(row) + lawn.cellHeight() * FOOT_LIFT;

        float previous = batch.getPackedColor();
        if (mode.isBrainEaten(row)) {
            batch.setColor(EATEN);
        }
        // Pre-divided: GameRenderer draws this whole pass through SpritePlacer's scaled transform.
        batch.draw(region, SpritePlacer.toSpriteSpace(x), SpritePlacer.toSpriteSpace(y),
                SpritePlacer.toSpriteSpace(width), SpritePlacer.toSpriteSpace(height));
        batch.setPackedColor(previous);
    }

    // Looked up once. A missing region is a legitimate answer -- Assets.region throws for an unknown id
    // -- and the lane still plays without its brain drawn.
    private TextureRegion brain() {
        if (!looked) {
            looked = true;
            try {
                brain = assets.region(BRAIN);
            } catch (RuntimeException missing) {
                com.badlogic.gdx.Gdx.app.error("IZombieRenderer", "no brain art at " + BRAIN);
            }
        }
        return brain;
    }

    public static IZombieMode modeOf(GameSession session) {
        if (session != null && session.getMode() instanceof IZombieMode izombie) {
            return izombie;
        }
        return null;
    }
}
