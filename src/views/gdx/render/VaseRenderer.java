package views.gdx.render;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import models.entities.interactables.GargantuarVase;
import models.entities.interactables.PlantVase;
import models.entities.interactables.Vase;
import models.game.GameSession;
import models.game.gamemodes.VaseBreakerMode;
import views.gdx.map.LawnGeometry;
import views.gdx.sprite.EntitySprite;
import views.gdx.sprite.SpriteRegistry;
import views.gdx.ui.UiArt;

import java.util.IdentityHashMap;
import java.util.Map;

// Vasebreaker's board: the vases, the smash when one goes, and the seed packets left lying about.
//
// Three vase sprites, because the mode already has three vase TYPES and a player who cannot tell them
// apart is playing a different game. `GargantuarVase` and `PlantVase` are classes rather than flags
// precisely so that "which vase is this" is answerable without reaching into its contents -- and it has
// to be answerable without doing that, since the whole point of an ordinary vase is that you do not
// know what is inside until it breaks.
//
// All three ship: VASE_BROWN, VASE_GREEN and VASE_GARGANTUAR are real PAM animations from the game's
// own VASEBREAKER group, each with idle / open / reveal / break clips.
public final class VaseRenderer {

    // Group-qualified: "VASE_GARGANTUAR" on its own resolves to the ZOMBIE animation -- the Gargantuar
    // that climbs OUT of the vase, two tiles tall and standing on the board in place of a pot. The other
    // two are unambiguous but are written the same way so the three read as one set.
    private static final String VASE_PLAIN = "VASEBREAKER/VASE_BROWN";
    private static final String VASE_PLANT = "VASEBREAKER/VASE_GREEN";
    private static final String VASE_GARGANTUAR = "VASEBREAKER/VASE_GARGANTUAR";

    private static final String CLIP_IDLE = "idle";
    private static final String CLIP_BREAK = "break";

    // How long a smashed vase keeps playing its break clip before it stops being drawn. The clip's own
    // duration is used when the sprite reports one; this is the fallback and the ceiling.
    private static final float BREAK_SECONDS = 0.8f;

    // A dropped packet lies flat on the tile rather than standing on it, so it reads as something on
    // the ground to be picked up rather than as a plant that has already been placed.
    private static final float SEED_WIDTH_FRACTION = 0.62f;
    private static final float SEED_ASPECT = 75f / 119f;
    private static final float SEED_LIFT = 0.16f;

    private final SpriteRegistry sprites;
    private final LawnGeometry lawn;
    private final AnimationClocks clocks;
    private final UiArt art;

    // Vases already seen broken, and how far into the smash each is.
    //
    // Identity-keyed, like every other per-entity map in this package: two vases holding the same thing
    // on different tiles are different vases, and Vase has no id of its own.
    private final Map<Vase, Float> smashing = new IdentityHashMap<>();
    private final java.util.Set<Vase> knownBroken =
            java.util.Collections.newSetFromMap(new IdentityHashMap<>());

    public VaseRenderer(SpriteRegistry sprites, LawnGeometry lawn, AnimationClocks clocks, UiArt art) {
        this.sprites = sprites;
        this.lawn = lawn;
        this.clocks = clocks;
        this.art = art;
    }

    // Drawn per lane, with the plants, so a vase in a nearer row covers one behind it -- the same
    // reason GameRenderer walks the lanes top to bottom in the first place.
    public void drawRow(Batch batch, GameSession session, int row, float delta) {
        VaseBreakerMode mode = modeOf(session);
        if (mode == null) {
            return;
        }
        for (Vase vase : mode.getVases()) {
            if (vase.getY() != row) {
                continue;
            }
            drawVase(batch, vase, delta);
        }
        for (int col = 0; col < utils.Constants.BOARD_COLS; col++) {
            String plant = mode.droppedSeedAt(col, row);
            if (plant != null) {
                drawDroppedSeed(batch, plant, col, row);
            }
        }
    }

    private void drawVase(Batch batch, Vase vase, float delta) {
        EntitySprite sprite = sprites.get(spriteFor(vase));
        float cx = lawn.centerX(vase.getX());
        float fy = footY(vase.getY());

        if (!vase.isBroken()) {
            float stateTime = clocks.advance(vase, CLIP_IDLE, delta);
            SpritePlacer.drawStanding(batch, sprite, CLIP_IDLE, stateTime, cx, fy, true, null);
            return;
        }

        // The frame a vase becomes broken is the only moment the view ever hears about it -- the model
        // records no event -- so the transition IS the trigger, exactly as a vanishing projectile is
        // for an impact.
        if (knownBroken.add(vase)) {
            smashing.put(vase, 0f);
        }
        Float elapsed = smashing.get(vase);
        if (elapsed == null) {
            return;   // smashed long enough ago; the tile is empty now
        }
        float next = elapsed + delta;
        float limit = Math.min(BREAK_SECONDS, duration(sprite));
        if (next >= limit) {
            smashing.remove(vase);
        } else {
            smashing.put(vase, next);
        }
        SpritePlacer.drawStanding(batch, sprite, CLIP_BREAK, elapsed, cx, fy, true, null);
    }

    private float duration(EntitySprite sprite) {
        float clip = sprite.clipDuration(CLIP_BREAK);
        return clip > 0f ? clip : BREAK_SECONDS;
    }

    // The pre-rendered seed packet for the plant inside, drawn small and flat on the tile.
    //
    // Not a PAM sprite: what is lying there is a PACKET, not a plant, and the game ships one per plant
    // in the UI_SEEDPACKETS atlas -- the same image the seed bank is built from, so a dropped Wall-nut
    // and a Wall-nut card are recognisably the same thing.
    private void drawDroppedSeed(Batch batch, String plantType, int col, int row) {
        TextureRegion packet = art.packet(plantType);
        if (packet == null) {
            return;
        }
        float width = lawn.cellWidth() * SEED_WIDTH_FRACTION;
        float height = width * SEED_ASPECT;
        float x = lawn.centerX(col) - width * 0.5f;
        float y = lawn.worldY(row) + lawn.cellHeight() * SEED_LIFT;

        // Pre-divided, because GameRenderer draws this whole pass through SpritePlacer's scaled
        // transform. Handing it world units directly would draw the packet at 64% of its size.
        batch.draw(packet, SpritePlacer.toSpriteSpace(x), SpritePlacer.toSpriteSpace(y),
                SpritePlacer.toSpriteSpace(width), SpritePlacer.toSpriteSpace(height));
    }

    private static String spriteFor(Vase vase) {
        if (vase instanceof GargantuarVase) {
            return VASE_GARGANTUAR;
        }
        if (vase instanceof PlantVase) {
            return VASE_PLANT;
        }
        return VASE_PLAIN;
    }

    // Same foot line the plants stand on, so a vase sits in its tile rather than on the line behind it.
    private float footY(int row) {
        return lawn.worldY(row) + lawn.cellHeight() * SpritePlacer.FOOT_INSET;
    }

    public static VaseBreakerMode modeOf(GameSession session) {
        if (session != null && session.getMode() instanceof VaseBreakerMode vaseBreaker) {
            return vaseBreaker;
        }
        return null;
    }
}
