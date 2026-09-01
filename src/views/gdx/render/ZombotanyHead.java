package views.gdx.render;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Rectangle;
import factories.zombie.ZombotanyRoster;
import views.gdx.sprite.ClipMap;
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

    // The resting clip every plant animation in the dump defines.
    private static final String PLANT_CLIP = "idle";

    // ## The head fires
    //
    // This used to be idle and nothing else, on the grounds that "a Peashooter head firing peas it does
    // not shoot would be the view promising something the model will not do". The premise was wrong in
    // one direction and has since changed in the other: ShootingAbility has always damaged plants down
    // the lane, and it now narrates each shot so the pea can be drawn (see ImpactEffects). So the head
    // does shoot, the pea is real, and a head that stared straight ahead while peas came out of it was
    // the promise being broken the other way round.
    //
    // Only the Peashooter has a `shooting` clip; firstAvailable drops the other three straight back to
    // idle, so this needs no test of which plant is on which zombie.
    private static final String[] SHOOT_CLIPS = {"shooting", "shoot", "attack"};

    // How long the head holds its shot pose. Deliberately not the clip's own length: the Peashooter's
    // `shooting` is authored as one bite of a continuous firing cycle, and ShootingAbility reloads for a
    // second and a half between peas, so playing it to the end would leave the head frozen on its last
    // frame for most of that gap. Just longer than the pea's flight, which is the beat being sold.
    private static final float SHOT_SECONDS = 0.4f;

    // Which zombies are mid-shot, and for how much longer. Identity-keyed, like every other per-zombie
    // map in the renderer: two zombies of the same species are equal for nothing that matters here.
    private final Map<models.entities.zombies.Zombie, Float> firing = new java.util.IdentityHashMap<>();

    // Shots that have been announced but not yet matched to a zombie.
    //
    // Same claim-at-draw-time dance ZombieActions documents, and for the same reason: the event names a
    // lane and an alias, never an object, and the model handing the view a Zombie is the dependency
    // ArchUnit forbids. The first plant-headed zombie drawn in that lane takes the shot.
    //
    // Aged rather than cleared each frame, because nothing here controls whether advance() runs before
    // or after the lane pass -- and a shot dropped on the frame it arrived would never be claimed at
    // all. Half a second is long enough to survive that ordering and short enough that an unclaimed
    // shot (its zombie died between the sentence and the frame) cannot fire the next zombie into the
    // lane instead.
    private static final float CLAIM_SECONDS = 0.5f;

    private static final class PendingShot {
        final int lane;
        float age;

        PendingShot(int lane) {
            this.lane = lane;
        }
    }

    private final java.util.List<PendingShot> pendingShots = new java.util.ArrayList<>();

    private static final java.util.regex.Pattern SPAT = java.util.regex.Pattern.compile(
            "^.+? spits a pea from \\((-?\\d+), (\\d+)\\) at .+? at \\((\\d+), (\\d+)\\)\\.$");

    // Offered every event the model drains, like every other effect that hangs off narration.
    public void onEvent(String message) {
        if (message == null) {
            return;
        }
        java.util.regex.Matcher spat = SPAT.matcher(message.trim());
        if (spat.matches()) {
            try {
                pendingShots.add(new PendingShot(Integer.parseInt(spat.group(2))));
            } catch (NumberFormatException ignored) {
                // a sentence shaped like a shot but not one
            }
        }
    }

    // Ages the shot poses. Once per frame from the renderer's sweep, never from the lane pass -- that
    // visits five times a frame and would run every shot at five times its own speed.
    // No sweep(): every entry in `firing` expires here on its own within SHOT_SECONDS, so a zombie that
    // dies mid-shot takes its entry with it a fraction of a second later.
    public void advance(float delta) {
        firing.replaceAll((zombie, left) -> left - delta);
        firing.values().removeIf(left -> left <= 0f);
        for (int i = pendingShots.size() - 1; i >= 0; i--) {
            PendingShot shot = pendingShots.get(i);
            shot.age += delta;
            if (shot.age >= CLAIM_SECONDS) {
                pendingShots.remove(i);
            }
        }
    }

    // What this head should be playing: its shot pose if this zombie has just fired, otherwise idle.
    private String clipFor(EntitySprite plant, models.entities.zombies.Zombie zombie) {
        if (zombie == null) {
            return PLANT_CLIP;
        }
        if (!firing.containsKey(zombie)) {
            claimShot(zombie);
        }
        return firing.containsKey(zombie)
                ? ClipMap.firstAvailable(plant, SHOOT_CLIPS[0], SHOOT_CLIPS[1], SHOOT_CLIPS[2])
                : PLANT_CLIP;
    }

    private void claimShot(models.entities.zombies.Zombie zombie) {
        int lane = zombie.getMovement().getPositionY();
        for (int i = 0; i < pendingShots.size(); i++) {
            if (pendingShots.get(i).lane == lane) {
                pendingShots.remove(i);
                firing.put(zombie, SHOT_SECONDS);
                return;
            }
        }
    }

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
        draw(batch, alias, body, clip, stateTime, x, footY, faceRight, null);
    }

    // The same, for a caller that is holding the zombie -- which is what lets the head play its shot
    // pose. DeathEffects uses the overload above: a corpse's zombie is already gone from the model, and
    // a severed head does not fire.
    public void draw(Batch batch, String alias, EntitySprite body, String clip, float stateTime,
                     float x, float footY, boolean faceRight,
                     models.entities.zombies.Zombie zombie) {
        String plantName = plantFor(alias);
        if (plantName == null || body == null) {
            return;
        }
        EntitySprite plant = sprites.get(plantName);
        if (plant == null || !plant.isReady() || !plant.hasClip(PLANT_CLIP)) {
            return;
        }
        // Measured against the plant's RESTING box whichever pose is playing: the box is what the head
        // is centred and sized on, and letting it change with the clip would make the head jump and
        // resize on the frame it fires.
        String headClip = clipFor(plant, zombie);
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
        float scale = headScale(alias, body, clip);
        // Seated off the RESTING skull, not the live frame's box: see restingSkull.
        float headY = originY - centreY - seat(restingSkull(body), plantBox, scale);

        // ## The head has to be MIRRORED
        //
        // Zombie animations are authored facing LEFT -- the way the horde walks -- and plant animations
        // are authored facing RIGHT, at the horde. So a plant dropped onto a zombie's neck as authored
        // comes out facing backwards: a Peashooter riding up the lawn with its muzzle pointed at the
        // house it is walking away from, spitting peas out of the back of its head.
        //
        // Passing `faceRight` to plant.draw did not and could not fix that -- that flag is libPVZ's LOOP
        // parameter and mirrors nothing at all (the same trap SpritePlacer.drawStandingScaled documents,
        // and which had every hypnotised zombie in the game walking backwards for its whole life). The
        // mirror is the transform's, here.
        //
        // Negated, not copied: `faceRight` is true for the zombie that has TURNED (hypnotised, or a
        // Prospector blown back down the lane), and that is the one case where the plant's own authored
        // facing is already correct.
        transform.begin(batch, headX, headY, scale, !faceRight);
        // Sampled against the PLANT's clip, not handed the zombie's raw stateTime. The two clips are
        // different lengths -- a 3s zombie walk over a 1.5s plant idle -- and an unsampled time runs off
        // the end of the shorter one, where a .PAM simply stops. The head froze for half of every walk
        // cycle. Same rule every other clip in the renderer goes through.
        // Drawn at MINUS its own centre so the plant's middle lands on the head point. Its own origin is
        // mid-body for a Peashooter and elsewhere again for a Squash, so neither can be assumed.
        plant.draw(batch, headClip,
                ClipMap.sample(plant, headClip, stateTime),
                -(plantBox.x + plantBox.width / 2f),
                plantBox.y + plantBox.height / 2f, faceRight);
        transform.end(batch);
    }

    // How far to drop the plant so it sits ON the neck instead of floating over it.
    //
    // The plant is drawn centred on the skull's centre, which is right only if the two are the same
    // size -- and they are not. A Peashooter's `idle` box is its whole body: head, stalk and leaves,
    // comfortably taller than it is wide, and the scale is taken from the WIDTHS (a head is as wide as
    // the head it replaces). So the drawn plant comes out considerably taller than the skull it stands
    // in for, and centring the two sticks half that surplus straight up above where the zombie's crown
    // used to be. That is the head riding high off the shoulders.
    //
    // Dropping by half the surplus aligns the two boxes at the TOP: the plant's crown lands exactly
    // where the skull's crown was, and the extra length hangs down the neck, which is where the stalk
    // and leaves of a plant-headed zombie belong anyway.
    //
    // SEAT is the dial. 1.0 aligns the crowns exactly; lower values leave the head sitting higher.
    private static final float SEAT = 1f;

    private static float seat(Rectangle skull, Rectangle plantBox, float scale) {
        if (skull == null || plantBox == null) {
            return 0f;
        }
        float surplus = plantBox.height * scale - skull.height;
        // Never lifted: a plant SHORTER than the skull it replaces is already inside the head's space,
        // and raising it would push it up off the neck for the opposite reason.
        return surplus <= 0f ? 0f : surplus / 2f * SEAT;
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

    // The skull's box at this instant -- on the very frame the body is being drawn on.
    //
    // Per frame rather than per clip on purpose: `partBounds(clip, part)` flattens the whole cycle into
    // one box, and a head placed at the middle of that box sits still while the body bobs underneath it
    // -- which reads as the plant being on a pole rather than on a neck. Same distinction WalkCycle
    // draws between partBounds and partBoundsByFrame, for the same reason.
    //
    // ## Why this asks the runtime instead of indexing the frames itself
    //
    // It used to walk `partBoundsByFrame` and LERP between the two frames either side of
    // `stateTime / duration * (frames.length - 1)`. Both halves of that were wrong, and together they
    // are why the head never quite sat on the neck:
    //
    //   * The interpolation itself. libPVZ does not blend frames -- it snaps to
    //     `floor(stateTime * frameRate)` and holds. So the body was on a frame while the head was
    //     somewhere between two, permanently, and the gap was largest exactly where the walk cycle moves
    //     fastest.
    //   * The `- 1`. Spreading N frames over [0, N-1] instead of [0, N) runs the head at (N-1)/N of the
    //     body's rate, so it fell steadily further behind across the cycle and snapped back at the loop
    //     -- a full frame of lag by the end of a 90-frame walk.
    //
    // Asking the animation for the part's box AT a time hands the whole question to the same frame
    // arithmetic the draw call uses, so the two cannot disagree by construction.
    private Rectangle skullAt(EntitySprite body, String clip, float stateTime) {
        for (String part : anchorOrder(body, clip)) {
            Rectangle box = body.partBoundsAt(clip, stateTime, part);
            if (box != null && box.width > 0f) {
                return box;
            }
        }
        return null;
    }

    // Which of the three head parts this clip actually poses, in preference order.
    //
    // Resolved once per body and clip because it means walking every frame of the clip through
    // partBoundsByFrame; the per-frame lookup above is the cheap one and runs every frame. An empty
    // answer is cached too -- a clip that poses none of them would otherwise be re-scanned forever.
    private final Map<String, String[]> anchorCache = new HashMap<>();

    private String[] anchorOrder(EntitySprite body, String clip) {
        String key = System.identityHashCode(body) + "#" + clip;
        String[] cached = anchorCache.get(key);
        if (cached != null) {
            return cached;
        }
        java.util.List<String> posed = new java.util.ArrayList<>();
        for (String part : ANCHOR_PARTS) {
            Rectangle[] frames = body.partBoundsByFrame(clip, part);
            if (frames != null && frames.length > 0) {
                posed.add(part);
            }
        }
        String[] order = posed.toArray(new String[0]);
        anchorCache.put(key, order);
        return order;
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
        Rectangle skull = restingSkull(body);
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

    // The skull's own box, from whichever of the body's clips actually poses it.
    //
    // Deliberately NOT per clip, and this is the whole reason the size and the position are measured
    // separately. The position falls back to the jaw when a clip does not pose the skull, and a jaw is
    // half a head wide -- so a size taken from the same box would halve the plant on exactly the clips
    // where the fallback fires. A head does not change size when its owner falls over.
    //
    // Both the SCALE and the SEAT read this rather than the live frame's box, and for the same reason.
    // A per-frame box breathes as the walk cycle tilts the head through it, and a seat measured off a
    // breathing box would bob the plant up and down against the neck it is supposed to be fixed to --
    // reintroducing, by a different route, exactly the drift the frame-sampling fix removed.
    //
    // Cached because it walks every clip of the animation, nulls included: a body with no skull part at
    // all would otherwise be re-scanned on every frame of every zombie wearing it.
    private final Map<String, Rectangle> restingSkulls = new HashMap<>();

    private Rectangle restingSkull(EntitySprite body) {
        String key = String.valueOf(System.identityHashCode(body));
        if (restingSkulls.containsKey(key)) {
            return restingSkulls.get(key);
        }
        Rectangle found = null;
        for (String clip : body.clips()) {
            Rectangle box = body.partBounds(clip, SKULL_PARTS[0]);
            if (box != null && box.width > 0f) {
                found = box;
                break;
            }
        }
        restingSkulls.put(key, found);
        return found;
    }

    // The clip a thrown head is drawn with, for the same reason headScale is public.
    public static String plantClip() {
        return PLANT_CLIP;
    }
}
