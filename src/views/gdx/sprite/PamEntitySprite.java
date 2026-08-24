package views.gdx.sprite;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.Batch;
import pvz.libpvz.pam.ClipRef;
import pvz.libpvz.pam.PamPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

// An entity drawn from a PopCap .PAM skeletal animation via libPVZ.
//
// PamPlayer can take a clip by name on every call, but that re-resolves the clip each frame. ClipRefs
// are the O(1) handle it offers instead, so they are looked up once and cached here -- there can be a
// dozen zombies on screen at 60 fps, and this is the inner loop.
final class PamEntitySprite implements EntitySprite {

    private final PamPlayer player;
    private final String pamPath;
    private final Set<String> availableClips;
    private final Set<String> availableParts;
    // The parts that carry an image, and the descendant closure of every name. Both exist for
    // visibleBounds -- see SpriteRegistry.collectDrawable and collectDescendants.
    private final Set<String> drawableParts;
    private final Map<String, Set<String>> descendants;
    private final Map<String, Float> clipDurations;
    private final Map<String, ClipRef> clipCache = new HashMap<>();

    // Clips whose absence has already been reported. Without this a missing "eat" clip logs once per
    // frame per zombie, which buries everything else in the console.
    private final Set<String> warnedClips = new java.util.HashSet<>();

    PamEntitySprite(PamPlayer player, String pamPath, Set<String> availableClips,
                    Set<String> availableParts, Set<String> drawableParts,
                    Map<String, Set<String>> descendants, Map<String, Float> clipDurations) {
        this.player = player;
        this.pamPath = pamPath;
        this.availableClips = availableClips;
        this.availableParts = availableParts;
        this.drawableParts = drawableParts;
        this.descendants = descendants;
        this.clipDurations = clipDurations;
    }

    @Override
    public float clipDuration(String clip) {
        Float seconds = clipDurations.get(clip);
        return seconds == null ? 0f : seconds;
    }

    @Override
    public void draw(Batch batch, String clip, float stateTime, float x, float y, boolean faceRight) {
        draw(batch, clip, stateTime, x, y, faceRight, null);
    }

    @Override
    public void draw(Batch batch, String clip, float stateTime, float x, float y, boolean faceRight,
                     Map<String, Boolean> parts) {
        ClipRef ref = resolve(clip);
        if (ref == null) {
            return;
        }
        try {
            if (parts == null || parts.isEmpty()) {
                player.draw(batch, ref, stateTime, x, y, faceRight);
            } else {
                player.draw(batch, ref, stateTime, x, y, faceRight, parts);
            }
        } catch (RuntimeException e) {
            // A malformed PAM must not take the frame down. Report once, then keep going -- a missing
            // zombie is a bug worth seeing on screen, a black window is not.
            if (warnedClips.add("draw:" + clip)) {
                Gdx.app.error("PamEntitySprite", "failed drawing " + pamPath + " [" + clip + "]", e);
            }
        }
    }

    private ClipRef resolve(String clip) {
        ClipRef cached = clipCache.get(clip);
        if (cached != null) {
            return cached;
        }
        if (!availableClips.contains(clip)) {
            if (warnedClips.add(clip)) {
                Gdx.app.log("PamEntitySprite", pamPath + " has no clip \"" + clip
                        + "\" (has: " + availableClips + ")");
            }
            return null;
        }
        ClipRef ref = player.getClip(pamPath, clip);
        if (ref != null) {
            clipCache.put(clip, ref);
        }
        return ref;
    }

    @Override
    public boolean isReady() {
        return !availableClips.isEmpty();
    }

    @Override
    public boolean isAnimated() {
        return true;
    }

    @Override
    public boolean hasClip(String clip) {
        return availableClips.contains(clip);
    }

    @Override
    public Set<String> clips() {
        return java.util.Collections.unmodifiableSet(availableClips);
    }

    @Override
    public boolean hasPart(String partName) {
        return availableParts.contains(partName);
    }

    // Cached: bounds() walks every frame of the clip to find its extent, which is far too expensive to
    // repeat per zombie per frame.
    private final Map<String, com.badlogic.gdx.math.Rectangle> boundsCache = new HashMap<>();

    @Override
    public com.badlogic.gdx.math.Rectangle bounds(String clip) {
        if (!availableClips.contains(clip)) {
            return null;
        }
        return boundsCache.computeIfAbsent(clip, c -> {
            try {
                return player.bounds(pamPath, c);
            } catch (RuntimeException e) {
                return null;
            }
        });
    }

    private final Map<String, com.badlogic.gdx.math.Rectangle> visibleCache = new HashMap<>();

    // Built by unioning the per-part boxes of everything NOT hidden, which is the only way round it:
    // libPVZ can report one part's extent but not "the extent of this subset", and bounds() cannot be
    // shrunk after the fact because there is no telling which part reached the edge.
    //
    // Cached per clip and hidden-set. It walks every part and every frame -- some zombies have ninety
    // parts -- so this is fine for a card built once and ruinous per frame. Nothing calls it per frame.
    @Override
    public com.badlogic.gdx.math.Rectangle visibleBounds(String clip, Set<String> hidden) {
        if (hidden == null || hidden.isEmpty()) {
            return bounds(clip);
        }
        String key = clip + "#" + new java.util.TreeSet<>(hidden);
        com.badlogic.gdx.math.Rectangle cached = visibleCache.get(key);
        if (cached != null) {
            return cached;
        }
        ClipRef ref = resolve(clip);
        if (ref == null) {
            return null;
        }
        com.badlogic.gdx.math.Rectangle union = union(ref, hidden);
        // A subset that measured to nothing means every part was skipped or unmeasurable. The full box is
        // wrong but drawable; null would blank the entity.
        com.badlogic.gdx.math.Rectangle answer = union == null ? bounds(clip) : union;
        if (answer != null) {
            visibleCache.put(key, answer);
        }
        return answer;
    }

    @Override
    public com.badlogic.gdx.math.Rectangle[] partBoundsByFrame(String clip, String partName) {
        ClipRef ref = resolve(clip);
        if (ref == null || partName == null) {
            return null;
        }
        try {
            return player.partBoundsByFrame(ref, partName);
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    public com.badlogic.gdx.math.Rectangle partBounds(String clip, String partName) {
        com.badlogic.gdx.math.Rectangle[] frames = partBoundsByFrame(clip, partName);
        if (frames == null) {
            return null;
        }
        com.badlogic.gdx.math.Rectangle union = null;
        for (com.badlogic.gdx.math.Rectangle frame : frames) {
            if (frame == null || frame.width <= 0f || frame.height <= 0f) {
                continue;
            }
            union = union == null ? new com.badlogic.gdx.math.Rectangle(frame) : union.merge(frame);
        }
        return union;
    }

    // Unions the drawable parts that survive `hidden`, expanded to descendants first.
    //
    // Only DRAWABLE parts, never the groups above them: libPVZ answers partBounds for any node, and for an
    // ancestor it hands back everything underneath. Asking about "root" therefore returns exactly
    // bounds(clip) -- which is how the first attempt at this excluded a zombie's three hats and got a box
    // with all three still in it, byte for byte.
    private com.badlogic.gdx.math.Rectangle union(ClipRef ref, Set<String> hidden) {
        Set<String> excluded = new java.util.HashSet<>();
        for (String name : hidden) {
            Set<String> family = descendants.get(name);
            excluded.addAll(family == null ? Set.of(name) : family);
        }

        com.badlogic.gdx.math.Rectangle union = null;
        for (String part : drawableParts) {
            if (excluded.contains(part)) {
                continue;
            }
            com.badlogic.gdx.math.Rectangle[] frames;
            try {
                frames = player.partBoundsByFrame(ref, part);
            } catch (RuntimeException e) {
                continue;   // a part this clip never poses; not an error
            }
            if (frames == null) {
                continue;
            }
            for (com.badlogic.gdx.math.Rectangle frame : frames) {
                if (frame == null || frame.width <= 0f || frame.height <= 0f) {
                    continue;
                }
                union = union == null ? new com.badlogic.gdx.math.Rectangle(frame) : union.merge(frame);
            }
        }
        return union;
    }

    private com.badlogic.gdx.math.Rectangle anchor;
    private boolean anchorResolved;

    // Prefers "idle" -- the canonical standing pose, present on almost every entity -- and otherwise
    // the smallest-area clip box, which is the one least likely to be inflated by parked parts.
    //
    // The staged plants have no plain "idle" (see PlantStages), so their first stage's idle is named
    // here too. Without it a Sun-shroom anchors on whichever of its fourteen clips happens to have the
    // smallest box -- possibly a stage-3 or plant-food pose -- and stands at the wrong height for the
    // whole level.
    private static final String[] ANCHOR_CLIPS = {ClipMap.IDLE, "idle_stage1", "idle_stage1_"};

    @Override
    public com.badlogic.gdx.math.Rectangle anchorBounds() {
        if (anchorResolved) {
            return anchor;
        }
        anchorResolved = true;

        String resting = null;
        for (String preferred : ANCHOR_CLIPS) {
            anchor = bounds(preferred);
            if (anchor != null) {
                resting = preferred;
                break;
            }
        }
        if (anchor == null) {
            for (String clip : availableClips) {
                com.badlogic.gdx.math.Rectangle candidate = bounds(clip);
                if (candidate == null) {
                    continue;
                }
                if (anchor == null
                        || candidate.width * candidate.height < anchor.width * anchor.height) {
                    anchor = candidate;
                    resting = clip;
                }
            }
        }
        standOnFeet(resting);
        return anchor;
    }

    // Parts whose name says they are what the entity stands on.
    //
    // "leg" is only taken together with "lower": an upper leg is a thigh, and a thigh's box reaches the
    // hip. `foot`, `toe` and `heel` need no qualifier.
    private static boolean isFootPart(String partName) {
        String lower = partName.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("foot") || lower.contains("toe") || lower.contains("heel")
                || (lower.contains("leg") && lower.contains("lower"));
    }

    // Moves the anchor's ground line from the bottom of the ARTWORK to the bottom of the FEET.
    //
    // The two are usually the same and for most entities this changes nothing. They are not the same
    // for a Gargantuar: its hammer hangs below its boots, so the idle box bottom is the hammer head,
    // and standing that on the lane's foot line lifted the whole Gargantuar a full row -- its feet came
    // out level with the row above's, which is exactly where a player sees it.
    //
    // This is the same shape of bug as bounds() counting switched-off armour, recorded during the
    // sprite-sizing work: a box that unions everything answers a question nobody asked. What the
    // placement actually needs is "where does this thing touch the ground", and the parts named for
    // feet are the honest answer to that.
    //
    // Only DRAWABLE parts are unioned, never groups or ancestors -- partBounds answers for any node and
    // returns the whole animation for `root`, which would silently undo the correction. And the union
    // takes the LOWEST point, so an Imp riding on a Gargantuar's back contributes its own feet without
    // affecting the result.
    private void standOnFeet(String restingClip) {
        if (anchor == null || restingClip == null) {
            return;
        }
        Float lowest = null;
        for (String part : drawableParts) {
            if (!isFootPart(part)) {
                continue;
            }
            com.badlogic.gdx.math.Rectangle box = partBounds(restingClip, part);
            if (box == null || box.width <= 0f) {
                continue;
            }
            // PAM bounds are y-DOWN, so the ground is the greatest y.
            float bottom = box.y + box.height;
            lowest = lowest == null ? bottom : Math.max(lowest, bottom);
        }
        if (lowest == null) {
            return;   // nothing named for a foot: keep the artwork's own bottom, as before
        }
        float was = anchor.y + anchor.height;
        // Only the ground line moves. The box's x, width and top are untouched, because bottomOffset is
        // the sole consumer and it reads nothing else.
        anchor = new com.badlogic.gdx.math.Rectangle(anchor.x, anchor.y,
                anchor.width, lowest - anchor.y);
        if (views.gdx.core.DebugFlags.LANE_CHECK && Math.abs(was - lowest) > 1f) {
            com.badlogic.gdx.Gdx.app.log("Anchor", pamPath() + " [" + restingClip
                    + "] ground line " + was + " -> " + lowest + " (moved "
                    + (was - lowest) + " units up to the feet)");
        }
    }

    String pamPath() {
        return pamPath;
    }
}
