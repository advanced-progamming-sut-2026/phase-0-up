package views.gdx.render;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Rectangle;
import factories.zombie.ZombotanyRoster;
import views.gdx.sprite.EntitySprite;
import views.gdx.sprite.SpriteRegistry;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

// The four Zombotany plant-zombies: an ordinary zombie body wearing a plant instead of a head.
//
// ## Why this class has to exist at all
//
// The handoff guessed T9.3 would be nearly free -- a `NAME_OVERRIDES` entry, at worst -- because
// Zombotany launches as an ordinary level and the board, bank and HUD therefore already work. It is
// not, and no override could have made it so: **`pvz-assets` ships no Zombotany art**. Neither
// `animations.json` nor `RESOURCES.json` contains the string "botany" in any case, so there is no
// animation to point at and no still image to fall back to either. The four of them drew literally
// nothing -- not a wrong sprite, not a silhouette, nothing -- which is why a Zombotany board looked
// like a level whose waves had failed to spawn.
//
// ## What is drawn instead, and why it is not "inventing art"
//
// A Zombotany zombie IS a zombie with a plant on its shoulders; that is the whole joke of the mode. So
// this composes one out of two things the dump does have:
//
//   * the shared zombie body (`ZOMBIE_TUTORIAL`), which every armored zombie already borrows -- so the
//     walk, the bite, the death and the foot planting are the real ones, for free; and
//   * the plant's own lawn animation -- the same PEASHOOTER / WALLNUT / JALAPENO / SQUASH that
//     `PlantRenderer` draws every level.
//
// Nothing is hand-drawn and nothing is guessed: the aliases come from `ZombotanyRoster`, the plant
// names from `plants.json`, and both halves are art the game already ships.
//
// ## Where the head goes
//
// Not a per-species offset table. The body's own `zombie_skull` part is measured, per FRAME, and the
// plant is drawn centred on wherever the skull would have been -- so the head nods with the walk, dips
// into the bite and topples with the corpse without any of that being written down here. This is the
// same reasoning `WalkCycle` uses for the feet: the artwork already knows, so ask it.
public final class ZombotanyHead {

    // The pieces of the shared body that a plant head replaces. All three come off together: the jaw
    // and the pupil are separate parts, and leaving either on puts a floating eye or a set of teeth
    // beside the plant.
    private static final String[] SKULL_PARTS = {"zombie_skull", "zombie_jaw", "zombie_pupil"};

    // The part measured to place the head, in preference order. The skull first: it is the biggest of
    // the three and the one whose box actually tracks the neck.
    //
    // A list rather than one name because a clip does not have to pose all three. `die` is the case that
    // forced it -- the shared body's collapse never poses `zombie_skull`, so a corpse came out headless
    // while every living zombie was fine. The jaw and the pupil sit inside the head, so either of them
    // still says where it is; the head simply comes out centred on a smaller box, which is why the size
    // is measured separately (see headScale) and does not shrink with the anchor.
    private static final String[] ANCHOR_PARTS = SKULL_PARTS;

    // Which plant sits on which zombie. Keyed by the registry alias so this cannot drift from the
    // roster: adding a fifth plant-zombie means adding it in both places, and a missing entry here
    // leaves that zombie bare-headed rather than silently drawing the wrong plant.
    private static final Map<String, String> PLANTS = buildPlants();

    private static Map<String, String> buildPlants() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(ZombotanyRoster.PEASHOOTER, "Peashooter");
        m.put(ZombotanyRoster.WALLNUT, "Wall-nut");
        m.put(ZombotanyRoster.JALAPENO, "Jalapeno");
        m.put(ZombotanyRoster.SQUASH, "Squash");
        return m;
    }

    // A plant head is drawn a little larger than the skull it replaces. Matched exactly, a Peashooter
    // reads as a small green growth rather than as the zombie's head -- in the original the plant is
    // conspicuously oversized, which is what makes the lane readable at a glance.
    private static final float HEAD_OVERSIZE = 1.15f;

    // The resting clip every plant animation in the dump defines. Deliberately NOT the plant's attack
    // clip: this head is scenery on a zombie, and a Peashooter head firing peas it does not shoot would
    // be the view promising something the model will not do.
    private static final String PLANT_CLIP = "idle";

    private final SpriteRegistry sprites;
    private final LocalTransform transform = new LocalTransform();

    // scale per plant, worked out once from the two animations' own measurements rather than tuned by
    // hand. Cached because both halves of it walk every frame of a clip.
    private final Map<String, Float> scales = new HashMap<>();

    public ZombotanyHead(SpriteRegistry sprites) {
        this.sprites = sprites;
    }

    // The plant this alias wears, or null for every other zombie in the game. The single question every
    // caller asks first; nothing else here fires for an ordinary zombie.
    public static String plantFor(String alias) {
        return alias == null ? null : PLANTS.get(alias);
    }

    // The visibility map to draw the BODY with: whatever it was already going to be drawn with, plus
    // the three skull parts switched off.
    //
    // Returns the map unchanged for an ordinary zombie, so callers can pipe every zombie through this
    // without a test of their own.
    public static Map<String, Boolean> hideSkull(String alias, EntitySprite body,
                                                 Map<String, Boolean> parts) {
        if (body == null || plantFor(alias) == null) {
            return parts;
        }
        Map<String, Boolean> hidden = parts == null ? new HashMap<>() : new HashMap<>(parts);
        for (String part : SKULL_PARTS) {
            if (body.hasPart(part)) {
                hidden.put(part, false);
            }
        }
        return hidden;
    }

    // Draws the plant where the skull would have been. Call it immediately after the body, with exactly
    // the arguments the body was drawn with -- the placement is derived from them.
    //
    // Silent no-op for an ordinary zombie, for a body whose skull this clip never poses, and for a plant
    // the dump happens not to carry, so a missing half can never take the frame down.
    public void draw(Batch batch, String alias, EntitySprite body, String clip, float stateTime,
                     float x, float footY, boolean faceRight) {
        String plantName = plantFor(alias);
        if (plantName == null || body == null) {
            return;
        }
        EntitySprite plant = sprites.get(plantName);
        if (plant == null || !plant.isReady() || !plant.hasClip(PLANT_CLIP)) {
            return;
        }
        Rectangle skull = skullAt(body, clip, stateTime);
        Rectangle plantBox = plant.bounds(PLANT_CLIP);
        if (skull == null || plantBox == null || plantBox.width <= 0f) {
            reportMissing(alias, clip, skull, plantBox);
            return;
        }

        // The body's draw origin, rebuilt exactly as SpritePlacer.drawStanding computes it -- the head
        // has to hang off the same point the body was placed from, or it drifts whenever the anchor
        // correction moves.
        float originX = SpritePlacer.toSpriteSpace(x);
        float originY = SpritePlacer.toSpriteSpace(footY) - SpritePlacer.bottomOffset(body, clip);

        // PAM bounds are y-DOWN, so a part's centre is at +cx, -cy from the origin. Mirroring the body
        // mirrors the neck with it, which is why the x half is negated for a hypnotised zombie.
        float centreX = skull.x + skull.width / 2f;
        float centreY = skull.y + skull.height / 2f;
        float headX = originX + (faceRight ? -centreX : centreX);
        float headY = originY - centreY;

        transform.begin(batch, headX, headY, headScale(alias, body, clip));
        // Drawn at MINUS its own centre so the plant's middle lands on the head point. Its own origin is
        // mid-body for a Peashooter and elsewhere again for a Squash, so neither can be assumed.
        plant.draw(batch, PLANT_CLIP, stateTime, -(plantBox.x + plantBox.width / 2f),
                plantBox.y + plantBox.height / 2f, faceRight);
        transform.end(batch);
    }

    // Why a head did not draw, said once per alias and clip.
    //
    // The failure is silent and looks exactly like a rendering bug in something else: the skull is
    // switched off before the body is drawn, so a head that cannot be placed leaves a HEADLESS zombie
    // walking down the lane rather than an ordinary one. Naming which half was missing is the
    // difference between a one-line fix and a hunt.
    //
    // One case is expected and not a fault: the shared body's `die` clip poses NONE of the three head
    // parts, so a corpse has no head to stand a plant on -- which is why the head is thrown on the frame
    // of death instead (see DeathEffects.popHead), and why an ordinary zombie's corpse is headless too.
    // A log rather than an error for exactly that reason.
    private final java.util.Set<String> reported = new java.util.HashSet<>();

    private void reportMissing(String alias, String clip, Rectangle skull, Rectangle plantBox) {
        if (!reported.add(alias + '#' + clip)) {
            return;
        }
        com.badlogic.gdx.Gdx.app.log("ZombotanyHead", alias + " [" + clip + "] draws no head: "
                + (skull == null ? "this clip poses none of " + java.util.Arrays.toString(ANCHOR_PARTS)
                : "the plant animation measured " + plantBox));
    }

    // The skull's box at this instant, interpolated between the two frames either side of it.
    //
    // Per frame rather than per clip on purpose: `partBounds` flattens the whole cycle into one box, and
    // a head placed at the middle of that box sits still while the body bobs underneath it -- which
    // reads as the plant being on a pole rather than on a neck. Same distinction WalkCycle draws
    // between partBounds and partBoundsByFrame, for the same reason.
    private final Rectangle scratch = new Rectangle();

    private Rectangle skullAt(EntitySprite body, String clip, float stateTime) {
        Rectangle[] frames = framesOf(body, clip);
        if (frames == null || frames.length == 0) {
            return null;
        }
        float duration = body.clipDuration(clip);
        float phase = duration <= 0f ? 0f : Math.min(Math.max(stateTime / duration, 0f), 1f);
        float position = phase * (frames.length - 1);
        int index = Math.min((int) position, frames.length - 1);
        Rectangle here = frames[index];
        Rectangle next = index + 1 < frames.length ? frames[index + 1] : here;
        if (here == null) {
            return next;
        }
        if (next == null) {
            return here;
        }
        float fraction = position - index;
        return scratch.set(
                here.x + (next.x - here.x) * fraction,
                here.y + (next.y - here.y) * fraction,
                here.width + (next.width - here.width) * fraction,
                here.height + (next.height - here.height) * fraction);
    }

    // partBoundsByFrame walks every frame of the clip, so it is cached per body-and-clip. Nulls are
    // cached too: a clip that never poses the skull would otherwise be re-scanned every frame forever.
    private final Map<String, Rectangle[]> frameCache = new HashMap<>();

    private Rectangle[] framesOf(EntitySprite body, String clip) {
        String key = System.identityHashCode(body) + "#" + clip;
        if (frameCache.containsKey(key)) {
            return frameCache.get(key);
        }
        Rectangle[] frames = null;
        for (String part : ANCHOR_PARTS) {
            frames = body.partBoundsByFrame(clip, part);
            if (frames != null && frames.length > 0) {
                break;
            }
        }
        frameCache.put(key, frames);
        return frames;
    }

    // How big to draw the plant: the skull's own width across this clip, times HEAD_OVERSIZE, divided
    // by the plant's authored width.
    //
    // Measured rather than tabled, so a plant authored at twice another's size still comes out the same
    // size on the same neck. The skull is measured across the WHOLE clip (partBounds, not the live
    // frame) -- using the live one would breathe the head in and out as the box tightened and loosened
    // through the cycle.
    //
    // Public because the head has to come off at the same size it was worn: see DeathEffects, which
    // throws it on the frame the zombie dies.
    public float headScale(String alias, EntitySprite body, String clip) {
        String plantName = plantFor(alias);
        if (plantName == null || body == null) {
            return 1f;
        }
        Float cached = scales.get(plantName);
        if (cached != null) {
            return cached;
        }
        EntitySprite plant = sprites.get(plantName);
        Rectangle skull = skullWidth(body);
        Rectangle plantBox = plant == null ? null : plant.bounds(PLANT_CLIP);
        float scale = skull == null || skull.width <= 0f || plantBox == null || plantBox.width <= 0f
                ? 1f : skull.width * HEAD_OVERSIZE / plantBox.width;
        scales.put(plantName, scale);
        if (views.gdx.core.DebugFlags.BOARD_COUNTS) {
            com.badlogic.gdx.Gdx.app.log("ZombotanyHead", plantName + " (asked on [" + clip
                    + "]): skull " + (skull == null ? 0f : skull.width) + " wide, plant "
                    + (plantBox == null ? 0f : plantBox.width) + " wide -> scale " + scale);
        }
        return scale;
    }

    // The skull's own width, from whichever of the body's clips actually poses it.
    //
    // Deliberately NOT per clip, and this is the whole reason the size and the position are measured
    // separately. The position falls back to the jaw when a clip does not pose the skull, and a jaw is
    // half a head wide -- so a size taken from the same box would halve the plant on exactly the clips
    // where the fallback fires. A head does not change size when its owner falls over.
    private Rectangle skullWidth(EntitySprite body) {
        for (String clip : body.clips()) {
            Rectangle box = body.partBounds(clip, SKULL_PARTS[0]);
            if (box != null && box.width > 0f) {
                return box;
            }
        }
        return null;
    }

    // The clip a thrown head is drawn with, for the same reason headScale is public.
    public static String plantClip() {
        return PLANT_CLIP;
    }
}
