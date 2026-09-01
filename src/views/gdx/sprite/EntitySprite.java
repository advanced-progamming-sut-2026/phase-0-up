package views.gdx.sprite;

import com.badlogic.gdx.graphics.g2d.Batch;

import java.util.Map;

// How every plant, zombie and prop gets drawn.
//
// The point of this interface is that renderers never talk to libPVZ. pvz-assets does not ship whole
// character frames -- only body parts plus .PAM skeletal data -- so the real implementation is a PAM
// runtime. But a handful of our entities have no PAM at all (Rotobaga, Cat-tail, Iceberg Lettuce and a
// few others simply are not in the asset dump), and any single PAM can fail to parse. Renderers must
// not care: they ask for a sprite, they get something drawable, and a missing animation degrades to a
// still image instead of taking a whole rendering phase down with it.
public interface EntitySprite {

    // stateTime is seconds since this entity entered its current clip, NOT since the level started --
    // otherwise every zombie on the lawn walks in perfect lockstep.
    void draw(Batch batch, String clip, float stateTime, float x, float y, boolean faceRight);

    // parts toggles individual pieces on and off by name: this is how zombie armor is rendered, since
    // a conehead is the base zombie animation with a cone part enabled rather than its own animation.
    // Implementations without parts (a still image) ignore it.
    void draw(Batch batch, String clip, float stateTime, float x, float y, boolean faceRight,
              Map<String, Boolean> parts);

    // False while an async atlas load is still in flight, or permanently for a sprite that resolved to
    // nothing at all. Callers may draw anyway -- it is always safe -- this is only for diagnostics.
    boolean isReady();

    // True when this is real skeletal animation rather than a still-image stand-in. Lets the HUD or a
    // debug overlay show which entities are running on the fallback path.
    boolean isAnimated();

    // Whether the underlying animation actually defines this clip. Callers use it to fall back from
    // "eat" to "idle" rather than drawing nothing.
    boolean hasClip(String clip);

    // Every clip this animation defines, in the order the dump lists them.
    //
    // The dump uses at least four different naming schemes for a resting pose and several plants have
    // no clip called "idle" at all, so PlantStages needs a genuine last resort -- "whatever this
    // animation's first clip is" -- rather than a guess list that can run out and leave a plant
    // invisible. Empty for a still-image fallback.
    java.util.Set<String> clips();

    // Whether this animation has a part of that name to toggle. Armor, status overlays and severed
    // limbs are all parts; asking first means a zombie whose animation lacks a piece just renders
    // without it instead of erroring.
    boolean hasPart(String partName);

    // The drawn extent of a clip, relative to the draw origin. Null when unknown.
    com.badlogic.gdx.math.Rectangle bounds(String clip);

    // The same extent counting only the parts that are actually drawn.
    //
    // bounds() unions every frame AND every part, switched-off ones included -- so every zombie sharing
    // the base body reports a box tall enough for the brick helmet whether or not it is wearing one. That
    // is invisible on the lawn, where entities are anchored by their feet, and very visible anywhere art
    // is scaled to FIT a box: a Conehead's card sized off that box draws it small, with a hat's worth of
    // dead space above its head.
    //
    // `hidden` names the parts to leave out. Empty or null gives exactly bounds(clip).
    com.badlogic.gdx.math.Rectangle visibleBounds(String clip, java.util.Set<String> hidden);

    // One part's own extent over a whole clip, unioned across its frames. Null when the clip never poses
    // it. This is the primitive the two above are built from, and what answers "which part is making this
    // box so big" -- the question no amount of staring at a screenshot can.
    com.badlogic.gdx.math.Rectangle partBounds(String clip, String partName);

    // One part's box at a given instant of a clip -- the SAME instant, and therefore the same frame,
    // that draw() would put on screen for that stateTime.
    //
    // This is the one to use for hanging something off a part. partBoundsByFrame hands back an array
    // with no rule for turning a stateTime into an index, and every attempt to invent that rule at the
    // call site is a chance to disagree with the renderer by a frame -- which is exactly how the
    // Zombotany head ended up lagging the neck it was supposed to sit on. Here the animation runtime
    // answers with its own frame arithmetic, so the two cannot drift.
    //
    // Default null for implementations with no frames to ask about (a still image has none, and neither
    // do the test doubles); callers must treat null as "cannot say".
    default com.badlogic.gdx.math.Rectangle partBoundsAt(String clip, float stateTime,
                                                         String partName) {
        return null;
    }

    // One part's box for each frame of a clip, in frame order. Null when the clip never poses it.
    //
    // Where partBounds flattens the motion, this keeps it -- which is the whole point for anything that
    // has to follow a part THROUGH a cycle rather than know how far it ranges. See WalkCycle, which reads
    // the ground_swatch marker's path to stop zombies' feet skating.
    com.badlogic.gdx.math.Rectangle[] partBoundsByFrame(String clip, String partName);

    // The extent to anchor this entity by, for ALL of its clips.
    //
    // Deliberately not per-clip. bounds() unions every frame of a clip, and some clips park hidden
    // parts far off-model: ZombieDefault's "walk" box is 421 tall against "idle"'s 250. Anchoring on
    // the playing clip therefore lifted walking zombies more than a full lane off the ground, made
    // them appear to hop lanes whenever they stopped to eat, and left them visually one row away from
    // the plants actually shooting them.
    //
    // One stable pose per entity keeps the feet still no matter what is playing.
    com.badlogic.gdx.math.Rectangle anchorBounds();

    // Length of a clip in seconds, or 0 when unknown. Needed because a clip does not repeat on its
    // own: played past its end it simply stops moving, which is why zombies appeared to walk only
    // while off-board (they enter at ~2.4s and the walk clip is 3.0s long).
    float clipDuration(String clip);
}
