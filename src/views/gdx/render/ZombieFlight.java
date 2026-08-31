package views.gdx.render;

import models.entities.zombies.Components.ActionState;
import models.entities.zombies.Zombie;
import views.gdx.sprite.EntitySprite;

import java.util.IdentityHashMap;
import java.util.Map;

// The Dodo Rider's hop over a Wall-nut, a mine or a Chomper.
//
// The model has flown over those obstacles since Phase 1 -- IgnoreObstaclesAbility raises a flag, the
// eat pass skips the plant and the mine underneath does not go off -- and the view was drawing the
// zombie walking straight through them. A rider that clears a Wall-nut without leaving the ground looks
// exactly like one that has walked through it, which is the bug the model had already fixed.
//
// This is a STATE rather than an event, which is what separates it from ZombieActions next door: there
// is no sentence to listen for, the answer is readable off the zombie on any frame, and it can start and
// stop as often as the board makes it. What is not readable is WHERE in the hop the zombie is, so that
// much is kept here: `fly_start` is the take-off, `fly_loop` is the air, `fly_end` is the landing, and
// the middle one is held for as long as the model says the rider is over something.
final class ZombieFlight {

    private static final String START = "fly_start";
    private static final String LOOP = "fly_loop";
    private static final String END = "fly_end";

    // Where in the hop a zombie is. LANDING outlives the model's flag by the length of `fly_end` -- the
    // rider is back on the ground as far as the model is concerned and still coming down on screen.
    private enum Phase { TAKING_OFF, AIRBORNE, LANDING }

    private static final class Hop {
        Phase phase;
        float elapsed;
    }

    // Identity, never equals: same reasoning as EntityInterpolator's and ZombieActions'.
    private final Map<Zombie, Hop> hops = new IdentityHashMap<>();

    // The clip this zombie should be playing instead of its state's, or null for "not in the air".
    //
    // Returns null for any animation without a `fly_loop`, which is every zombie but the Dodo Rider --
    // so this costs one map lookup for the rest of the horde and cannot put a clip on art that has none.
    String clipFor(Zombie zombie, EntitySprite sprite) {
        if (zombie == null || sprite == null || !sprite.hasClip(LOOP)) {
            return null;
        }
        Hop hop = hops.get(zombie);
        if (flying(zombie)) {
            return rising(zombie, sprite, hop);
        }
        if (hop == null) {
            return null;
        }
        return falling(zombie, sprite, hop);
    }

    // A dying rider falls out of the sky rather than finishing its hop: `die` is the louder clip and the
    // model leaves the flag set on the tick it dies.
    private static boolean flying(Zombie zombie) {
        return zombie.getState().isFlying()
                && zombie.getState().getCurrentAction() != ActionState.DYING;
    }

    private String rising(Zombie zombie, EntitySprite sprite, Hop existing) {
        Hop hop = existing;
        if (hop == null) {
            hop = new Hop();
            hop.phase = sprite.hasClip(START) ? Phase.TAKING_OFF : Phase.AIRBORNE;
            hops.put(zombie, hop);
        } else if (hop.phase == Phase.LANDING) {
            // Down for a moment and over something else already -- two obstacles on adjoining tiles.
            // Straight back into the air rather than through the take-off again, which at this range
            // reads as a stumble.
            hop.phase = Phase.AIRBORNE;
            hop.elapsed = 0f;
        }
        if (hop.phase == Phase.TAKING_OFF && hop.elapsed >= sprite.clipDuration(START)) {
            hop.phase = Phase.AIRBORNE;
            hop.elapsed = 0f;
        }
        return hop.phase == Phase.TAKING_OFF ? START : LOOP;
    }

    private String falling(Zombie zombie, EntitySprite sprite, Hop hop) {
        if (hop.phase != Phase.LANDING) {
            if (!sprite.hasClip(END)) {
                hops.remove(zombie);
                return null;
            }
            hop.phase = Phase.LANDING;
            hop.elapsed = 0f;
        }
        if (hop.elapsed >= sprite.clipDuration(END)) {
            hops.remove(zombie);
            return null;
        }
        return END;
    }

    // Once per frame, from ZombieRenderer's sweep -- NOT from clipFor, which is called once per zombie
    // per frame and would age a hop at whatever rate the lane pass happens to visit it.
    void advance(float delta) {
        for (Hop hop : hops.values()) {
            hop.elapsed += delta;
        }
    }

    // Drops riders that are gone, so a long level does not accumulate an entry for every one that ever
    // cleared a Wall-nut.
    void sweep() {
        hops.keySet().removeIf(zombie -> zombie.getHealth() == null
                || zombie.getHealth().getTotalHP() <= 0);
    }
}
