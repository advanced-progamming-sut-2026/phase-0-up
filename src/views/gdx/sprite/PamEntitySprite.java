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
    private final Map<String, ClipRef> clipCache = new HashMap<>();

    // Clips whose absence has already been reported. Without this a missing "eat" clip logs once per
    // frame per zombie, which buries everything else in the console.
    private final Set<String> warnedClips = new java.util.HashSet<>();

    PamEntitySprite(PamPlayer player, String pamPath, Set<String> availableClips,
                    Set<String> availableParts) {
        this.player = player;
        this.pamPath = pamPath;
        this.availableClips = availableClips;
        this.availableParts = availableParts;
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

    String pamPath() {
        return pamPath;
    }
}
