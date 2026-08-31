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

    // KillPlantsAbility, on the branch that does NOT require a torch: "ZombieGargantuar smashes
    // Peashooter to pieces at (3, 2)." The torch branch says "sets ... ablaze at (" and must not match --
    // a zombie setting a plant alight is not swinging anything.
    //
    // This is the `smash` clip T8.1 names, and it was the one entry on that task's list playing nowhere:
    // the model pulverises a plant in a single call, so ActionState says WALKING right through the blow
    // and a Gargantuar used to flatten a Wall-nut without moving a muscle. The same sentence also raises
    // the camera shake, from the other side of the fan-out.
    private static final Pattern SMASH =
            Pattern.compile("^(.+?) smashes (.+?) to pieces at \\((\\d+), (\\d+)\\)\\.$");

    // SummonGraveAbility, at the START of its cast: "The Tomb Raiser starts chanting for the dead."
    //
    // The chant, not the result. "raises a grave at (x, y)" is the moment the stone is already standing,
    // and starting a three-second animation there would have the raiser gesture at work it had finished
    // -- so the model now announces the wind-up too, and the ability holds the graves back for exactly
    // as long as this clip runs. See SummonGraveAbility.CHANT_TICKS.
    private static final Pattern CHANT =
            Pattern.compile("^The Tomb Raiser starts chanting for the dead\\.$");

    // ThrowIceAbility, at the START of its throw: "ZombieIceAgeHunter takes aim at Peashooter at (3, 1)."
    //
    // The aim, not the hit. "hurls ice at" is the moment the plant is already frozen, and starting a
    // two-second animation there would have the snowball land before the arm moved -- so the model now
    // announces the wind-up too and holds the ice back for exactly as long as this clip runs. See
    // ThrowIceAbility.AIM_TICKS; the hit sentence is what ImpactEffects draws the splat from.
    //
    // The lane IS the zombie's here, unlike the two above: the Hunter only throws at plants in its own
    // row, so the tile in the sentence and the thrower's lane are the same number.
    private static final Pattern THROW_ICE =
            Pattern.compile("^(.+?) takes aim at (.+?) at \\((\\d+), (\\d+)\\)\\.$");

    // ThrowOctopusAbility, at the START of its throw: "ZombieBeachOctopus winds up an octopus at (7, 2)."
    //
    // The tile is the THROWER's, not the target's -- the octopus has not left its hand yet, and the
    // whole point of the sentence is to find the zombie that is about to swing. See
    // ThrowOctopusAbility.TOSS_TICKS; the octopus itself is flown by ImpactEffects off the release.
    private static final Pattern TOSS_OCTOPUS =
            Pattern.compile("^(.+?) winds up an octopus at \\((\\d+), (\\d+)\\)\\.$");

    // TurnIntoSheep, at the START of its cast: "ZombieWizard raises its staff at (6, 1)."
    //
    // The wizard's own tile, like the octopus toss: nothing is hexed yet and what this has to find is
    // the zombie about to cast. See TurnIntoSheep.CAST_TICKS -- the plant changes when the clip ends.
    private static final Pattern CAST_SHEEP =
            Pattern.compile("^(.+?) raises its staff at \\((\\d+), (\\d+)\\)\\.$");

    private static final String IMP_ALIAS = "ZombieImp";
    private static final String RAISER_ALIAS = "ZombieTombRaiser";

    // The thrower: wind up, then launch.
    private static final String[] THROWER_CLIPS = {"fire", "cannon_fire"};
    // The Imp: airborne, then the landing.
    private static final String[] IMP_CLIPS = {"fly", "land"};
    // The hammer. DARK_GARGANTUAR ships `smash_left` (1.77s) and no `smash_right`; start() keeps only
    // the clips a sprite actually has, so naming both costs nothing and covers the art that has them.
    private static final String[] SMASH_CLIPS = {"smash_left", "smash_right"};
    // The snowball. ZOMBIE_ICEAGE_HUNTER's one clip that is not a state, and the reason a Hunter used
    // to freeze a plant across the lawn without so much as raising an arm.
    private static final String[] THROW_CLIPS = {"throw"};
    // The octopus. ZOMBIE_BEACH_OCTOPUS ships five idles, a walk, an eat, a die -- and `toss`, which is
    // the only one of the nine that was never reachable from an ActionState.
    private static final String[] TOSS_CLIPS = {"toss"};
    // The hex. ZOMBIE_DARK_WIZARD's `sheep` -- the clip is named after what it does to the plant, and
    // like `toss` and `power` it was the one clip in that animation no ActionState could reach.
    private static final String[] SHEEP_CLIPS = {"sheep"};
    // Raising the dead. ZOMBIE_EGYPT_TOMBRAISER's one clip that is not a state: `power`, 3s of it
    // hurling bones at the ground. At the raiser's 0.185 cells/second that is barely half a tile of
    // walking, so the zombie does not visibly skate through it.
    private static final String[] RAISE_CLIPS = {"power"};

    // A claim any zombie of the right species can take, wherever it is standing.
    //
    // The other two events name the tile they happen ON, which for a thrown Imp or a smashed plant is
    // also the lane the zombie is in. The chant names no tile at all -- the Tomb Raiser reaches anywhere
    // on the board, and where its stones will land is not decided until three seconds later -- so there
    // is no lane to match on. The trade is that with two raisers on screen the wrong one may be the one
    // that gestures. Both are on the same cooldown and one of them is genuinely chanting, so what the
    // player sees is a Tomb Raiser raising a grave either way.
    private static final int ANY_LANE = -1;

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
        String alias;
        String[] clips;
        int lane;
        float age;
        boolean claimed;
        // The imp throw is the one event with TWO parts to hand out -- the Gargantuar's launch and the
        // Imp's arrival. Null for a single-actor event like the smash, which is most of them.
        String partnerAlias;
        String[] partnerClips;
        boolean partnerClaimed;

        boolean finished() {
            return claimed && (partnerAlias == null || partnerClaimed);
        }
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
        String text = message.trim();
        Matcher throwing = THROW.matcher(text);
        if (throwing.matches()) {
            Claim claim = raise(throwing.group(1).trim(), THROWER_CLIPS,
                    Integer.parseInt(throwing.group(3)), "throws an Imp");
            claim.partnerAlias = IMP_ALIAS;
            claim.partnerClips = IMP_CLIPS;
            return;
        }
        // Group 4 is the row: the sentence is "... at (col, row).", so the lane is the SECOND number.
        Matcher smashing = SMASH.matcher(text);
        if (smashing.matches()) {
            raise(smashing.group(1).trim(), SMASH_CLIPS,
                    Integer.parseInt(smashing.group(4)), "swings at " + smashing.group(2).trim());
            return;
        }
        Matcher casting = CAST_SHEEP.matcher(text);
        if (casting.matches()) {
            raise(casting.group(1).trim(), SHEEP_CLIPS,
                    Integer.parseInt(casting.group(3)), "casts a sheep hex");
            return;
        }
        Matcher tossing = TOSS_OCTOPUS.matcher(text);
        if (tossing.matches()) {
            raise(tossing.group(1).trim(), TOSS_CLIPS,
                    Integer.parseInt(tossing.group(3)), "winds up an octopus");
            return;
        }
        Matcher throwingIce = THROW_ICE.matcher(text);
        if (throwingIce.matches()) {
            raise(throwingIce.group(1).trim(), THROW_CLIPS,
                    Integer.parseInt(throwingIce.group(4)),
                    "takes aim at " + throwingIce.group(2).trim());
            return;
        }
        // The alias is not in the sentence -- the model names the zombie the way a player would -- so it
        // is supplied here. Only one species raises graves, so there is nothing to disambiguate.
        if (CHANT.matcher(text).matches()) {
            raise(RAISER_ALIAS, RAISE_CLIPS, ANY_LANE, "chants up a grave");
        }
    }

    private Claim raise(String alias, String[] clips, int lane, String what) {
        Claim claim = new Claim();
        claim.alias = alias;
        claim.clips = clips;
        claim.lane = lane;
        claims.add(claim);
        if (views.gdx.core.DebugFlags.BOARD_COUNTS) {
            com.badlogic.gdx.Gdx.app.log("ZombieActions", alias + " " + what + " in lane " + lane);
        }
        return claim;
    }

    // Ages every sequence and drops the finished ones. Called once per frame, NOT per zombie: advancing
    // inside clipFor would run a sequence at however many times the frame rate the lane pass happens to
    // visit that zombie.
    public void advance(float delta) {
        for (int i = claims.size() - 1; i >= 0; i--) {
            Claim claim = claims.get(i);
            claim.age += delta;
            if (claim.age >= CLAIM_SECONDS || claim.finished()) {
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
            if (claim.lane != ANY_LANE && claim.lane != lane) {
                continue;
            }
            if (!claim.claimed && alias.equalsIgnoreCase(claim.alias)) {
                claim.claimed = true;
                return start(zombie, sprite, claim.clips);
            }
            if (claim.partnerAlias != null && !claim.partnerClaimed
                    && claim.partnerAlias.equalsIgnoreCase(alias)) {
                claim.partnerClaimed = true;
                return start(zombie, sprite, claim.partnerClips);
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
