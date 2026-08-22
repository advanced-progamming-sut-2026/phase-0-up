package views.gdx.render;

import models.entities.zombies.Zombie;
import views.gdx.sprite.EntitySprite;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// One-shot animations a zombie plays because something HAPPENED, rather than because of the state it is
// in.
//
// ClipMap.forZombie answers the standing question -- walking, eating, dying, idle -- off the zombie's
// ActionState, and that is all the model has. The Gargantuar throwing its Imp is not a state: it is an
// instant, over in half a second, and the model records it only as a sentence. So this listens for that
// sentence the same way ExplosionEffects and WeatherEffects listen for theirs, and drives a short clip
// sequence on top of whatever the zombie would otherwise be playing.
//
// The art is the reason this exists at all. DARK_GARGANTUAR ships `fire` and `cannon_fire` -- the wind-up
// and the launch -- and ZOMBIE_DARK_IMP_MONK ships `fly` and `land`, which is the Imp arriving. Between
// them that is the whole throw, drawn from both ends. The plain GARGANTUAR/GARGANTUAR_IMP pair the
// registry used before had none of it and could only stand there while an Imp appeared out of nowhere
// three columns away.
public final class ZombieActions {

    // ThrowImp.execute: "ZombieGargantuar hurls its Imp over your defences onto (2, 3)!"
    private static final Pattern THROW =
            Pattern.compile("^(.+?) hurls its Imp over your defences onto \\((\\d+), (\\d+)\\)!$");

    private static final String IMP_ALIAS = "ZombieImp";

    // The thrower: wind up, then launch.
    private static final String[] THROWER_CLIPS = {"fire", "cannon_fire"};
    // The Imp: airborne, then the landing.
    private static final String[] IMP_CLIPS = {"fly", "land"};

    // `fly` is a single frame (0.0333s in the dump) -- it is a POSE, not a movement, so it is held for a
    // readable beat instead of being played for its own length. Anything shorter and the Imp is on the
    // ground before the eye reaches it.
    private static final float MIN_CLIP_SECONDS = 0.32f;

    // How long a throw stays claimable. The event and the Imp's first frame are the same tick, so this
    // only has to survive a frame or two; a second is generous and keeps a missed claim from lingering
    // into the next Gargantuar's throw.
    private static final float CLAIM_SECONDS = 1f;

    // A throw that has been announced but not yet matched to the zombies on screen.
    //
    // The event names an alias and a lane, not an object, and the view has no way to be handed the
    // Zombie itself without the model reaching into the view -- the dependency ArchUnit forbids. So the
    // claim happens at DRAW time, when the renderer is holding the actual zombie: the first Gargantuar
    // drawn in that lane takes the thrower's part and the first Imp takes the Imp's.
    private static final class Claim {
        String throwerAlias;
        int lane;
        float age;
        boolean throwerClaimed;
        boolean impClaimed;
    }

    // A clip sequence in progress on one zombie.
    private static final class Sequence {
        String[] clips;
        int index;
        float elapsed;
        float current;      // duration of clips[index]
    }

    private final List<Claim> claims = new ArrayList<>();
    // Identity, never equals: two zombies of the same species are equal for nothing that matters here,
    // and Zombie does not override equals anyway. Same reasoning as EntityInterpolator's.
    private final Map<Zombie, Sequence> running = new IdentityHashMap<>();

    // Offered every event the model drains, alongside the explosions and the toasts. Anything that is
    // not a throw is ignored.
    public void onEvent(String message) {
        if (message == null) {
            return;
        }
        Matcher matcher = THROW.matcher(message.trim());
        if (!matcher.matches()) {
            return;
        }
        Claim claim = new Claim();
        claim.throwerAlias = matcher.group(1).trim();
        claim.lane = Integer.parseInt(matcher.group(3));
        claims.add(claim);
        if (views.gdx.core.DebugFlags.BOARD_COUNTS) {
            com.badlogic.gdx.Gdx.app.log("ZombieActions",
                    claim.throwerAlias + " throws an Imp in lane " + claim.lane);
        }
    }

    // Ages every sequence and drops the finished ones. Called once per frame, NOT per zombie: advancing
    // inside clipFor would run a sequence at however many times the frame rate the lane pass happens to
    // visit that zombie.
    public void advance(float delta) {
        for (int i = claims.size() - 1; i >= 0; i--) {
            Claim claim = claims.get(i);
            claim.age += delta;
            if (claim.age >= CLAIM_SECONDS || (claim.throwerClaimed && claim.impClaimed)) {
                claims.remove(i);
            }
        }
        running.values().removeIf(sequence -> {
            sequence.elapsed += delta;
            while (sequence.elapsed >= sequence.current) {
                sequence.elapsed -= sequence.current;
                sequence.index++;
                if (sequence.index >= sequence.clips.length) {
                    return true;   // sequence over; the zombie goes back to its ActionState clip
                }
                sequence.current = MIN_CLIP_SECONDS;   // re-measured against the sprite in clipFor
            }
            return false;
        });
    }

    // The clip this zombie should be playing instead of its state's, or null for "nothing special".
    //
    // Claiming happens here because this is the only place the renderer and the actual Zombie object are
    // in the same room.
    public String clipFor(Zombie zombie, EntitySprite sprite) {
        if (zombie == null || sprite == null) {
            return null;
        }
        Sequence sequence = running.get(zombie);
        if (sequence == null) {
            sequence = tryClaim(zombie, sprite);
        }
        if (sequence == null) {
            return null;
        }
        String clip = sequence.clips[sequence.index];
        // Re-measured each frame rather than stored, because the duration depends on the sprite and the
        // sprite may not have been ready when the sequence started.
        sequence.current = Math.max(MIN_CLIP_SECONDS, sprite.clipDuration(clip));
        return clip;
    }

    private Sequence tryClaim(Zombie zombie, EntitySprite sprite) {
        int lane = zombie.getMovement().getPositionY();
        String alias = zombie.getAlias();
        for (Claim claim : claims) {
            if (claim.lane != lane) {
                continue;
            }
            if (!claim.throwerClaimed && alias.equalsIgnoreCase(claim.throwerAlias)) {
                claim.throwerClaimed = true;
                return start(zombie, sprite, THROWER_CLIPS);
            }
            if (!claim.impClaimed && IMP_ALIAS.equalsIgnoreCase(alias)) {
                claim.impClaimed = true;
                return start(zombie, sprite, IMP_CLIPS);
            }
        }
        return null;
    }

    // Only the clips the sprite actually has. A sequence of names it does not carry would fall through
    // firstAvailable to `idle` and read as the zombie stopping dead mid-throw.
    private Sequence start(Zombie zombie, EntitySprite sprite, String[] wanted) {
        List<String> available = new ArrayList<>(wanted.length);
        for (String clip : wanted) {
            if (sprite.hasClip(clip)) {
                available.add(clip);
            }
        }
        if (available.isEmpty()) {
            return null;
        }
        Sequence sequence = new Sequence();
        sequence.clips = available.toArray(new String[0]);
        sequence.current = Math.max(MIN_CLIP_SECONDS, sprite.clipDuration(sequence.clips[0]));
        running.put(zombie, sequence);
        if (views.gdx.core.DebugFlags.BOARD_COUNTS) {
            com.badlogic.gdx.Gdx.app.log("ZombieActions", zombie.getAlias() + " plays "
                    + String.join(" -> ", sequence.clips)
                    + (available.size() == wanted.length ? ""
                            : " (of " + String.join(",", wanted) + " -- the rest are not in the art)"));
        }
        return sequence;
    }

    // Drops sequences belonging to zombies that are gone, so a long level does not accumulate entries
    // for every Gargantuar that ever threw. Called from GameRenderer's per-frame sweep.
    //
    // Sequences also self-expire after a second or so, so this is belt-and-braces rather than the only
    // thing keeping the map small.
    void sweep() {
        running.keySet().removeIf(zombie -> zombie.getHealth() == null
                || zombie.getHealth().getTotalHP() <= 0);
    }
}
