package views.gdx.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import models.entities.zombies.Zombie;
import views.gdx.bridge.EntityInterpolator;
import views.gdx.map.LawnGeometry;
import views.gdx.sprite.ArmorVisibility;
import views.gdx.sprite.ClipMap;
import views.gdx.sprite.EntitySprite;
import views.gdx.sprite.SpriteRegistry;
import views.gdx.sprite.WalkCycle;

import java.util.Map;

// Draws the zombies of one lane.
//
// Two things make this more than "look up sprite, draw at x":
//
//  * Position must be interpolated. The model moves a zombie ~2.2 world px per tick at 10 Hz; drawn
//    straight from the model it visibly stutters at 60 fps.
//  * A zombie's lane is interpolated too. A lane switch is an instant reassignment in the model, so
//    blending it turns a teleport into a hop across the row boundary.
public final class ZombieRenderer {

    // Status tints. The spec only requires statuses be "visually distinct, at least by changing the
    // zombie's colour"; frozen and chilled get the blues, hypnotised gets the purple it has in the
    // original. Butter is handled as a real part by ArmorVisibility instead of a tint.
    private static final Color FROZEN = new Color(0.55f, 0.80f, 1f, 1f);
    private static final Color CHILLED = new Color(0.75f, 0.88f, 1f, 1f);
    private static final Color HYPNOTISED = new Color(0.85f, 0.55f, 1f, 1f);
    // Submerged zombies are under the water line and only partly visible.
    private static final Color SUBMERGED = new Color(1f, 1f, 1f, 0.45f);

    private final SpriteRegistry sprites;
    private final LawnGeometry lawn;
    private final EntityInterpolator interpolator;
    private final AnimationClocks clocks;

    // Watches health frame to frame; a drop is a hit. See DamageFlash.
    private final DamageFlash flashes = new DamageFlash();

    // Event-driven one-shots (the Gargantuar's imp throw, the Imp's landing).
    private final ZombieActions actions = new ZombieActions();

    public ZombieActions actions() {
        return actions;
    }

    public ZombieRenderer(SpriteRegistry sprites, LawnGeometry lawn, EntityInterpolator interpolator,
                          AnimationClocks clocks) {
        this.sprites = sprites;
        this.lawn = lawn;
        this.interpolator = interpolator;
        this.clocks = clocks;
    }

    // I, Zombie's sun makers. The model spawns them as plain bucketheads pinned at speed 0, because it
    // wants an ordinary destructible zombie with an ordinary zombie's HP -- but a zombie that stands
    // still in the back column and prints money is doing something no buckethead does, and it has to
    // look like it. The game's disco mech is the machine that fits.
    private static final String SUN_PRODUCER_SPRITE = "ZOMBIE_MECH_DISCO";

    public void draw(Batch batch, Zombie zombie, float delta, float alpha) {
        EntitySprite sprite = sprites.get(spriteNameFor(zombie));
        // An event-driven one-shot wins over the state's clip: the Gargantuar's throw and the Imp's
        // landing are instants the model records only as a sentence, so ActionState still says WALKING
        // all the way through them. See ZombieActions.
        String action = actions.clipFor(zombie, sprite);
        String clip = action != null ? action : ClipMap.forZombie(sprite, zombie);
        // Per zombie, restarted on clip change: otherwise the whole horde steps in unison and a
        // zombie that stops to bite starts its "eat" animation halfway through.
        // A frozen zombie's animation stops dead. The model already holds its x still, but the walk
        // clip kept playing -- so it marched on the spot and read as "the freeze does nothing". Passing
        // 0 here holds the pose instead; the clock is still touched so AnimationClocks does not sweep
        // the entry and restart the walk from frame 0 when it thaws.
        float animationDelta = zombie.getState().isFrozen() ? 0f : delta;
        float stateTime = ClipMap.sample(sprite, clip, clocks.advance(zombie, clip, animationDelta));

        float modelX = (float) zombie.getMovement().getPositionX();
        int modelLane = zombie.getMovement().getPositionY();

        float x = lawn.worldX(interpolator.x(zombie, modelX, alpha)) + footPlanting(zombie, sprite,
                clip, stateTime);
        float lane = interpolator.lane(zombie, modelLane, alpha);
        float footY = laneFootY(lane);

        // No armour map for the mech: its parts are its own, and a bucket toggle would name nothing.
        Map<String, Boolean> parts = isSunProducer(zombie)
                ? null : ArmorVisibility.forZombie(zombie, sprite);

        Color previous = batch.getColor().cpy();
        batch.setColor(tintFor(zombie));

        // Zombies walk right-to-left, so they face LEFT -- except a hypnotised one, which has turned
        // around and is walking back the other way for the player.
        boolean faceRight = zombie.getState().isHypnotized();
        SpritePlacer.drawStandingScaled(batch, sprite, clip, stateTime, x, footY, faceRight, parts,
                scaleFor(zombie));

        // Hit flash: the same frame again, additively, so the zombie lights up white. Total HP, not the
        // body's -- a cone or a bucket absorbing a pea is still a hit, and the zombie should react.
        float flash = zombie.getHealth() == null ? 0f
                : flashes.intensity(zombie, zombie.getHealth().getTotalHP(), delta);
        if (flash > 0f) {
            SpritePlacer.beginAdditive(batch);
            batch.setColor(flash, flash, flash, 1f);
            SpritePlacer.drawStandingScaled(batch, sprite, clip, stateTime, x, footY, faceRight, parts,
                scaleFor(zombie));
            SpritePlacer.endAdditive(batch);
        }

        batch.setColor(previous);
    }

    // The disco mech is authored about half again the size of a browncoat -- fine for the one-off boss
    // it is in the real game, wrong for five of them parked in a column, where each overlapped the lane
    // above and below. Brought down to roughly a normal zombie's footprint.
    private static final float SUN_PRODUCER_SCALE = 0.62f;

    private String spriteNameFor(Zombie zombie) {
        return isSunProducer(zombie) ? SUN_PRODUCER_SPRITE : zombie.getAlias();
    }

    private float scaleFor(Zombie zombie) {
        return isSunProducer(zombie) ? SUN_PRODUCER_SCALE : 1f;
    }

    // Asked of the MODE, which is the only thing that knows which five of the board's zombies it
    // designated as makers -- they are otherwise ordinary bucketheads and indistinguishable from any
    // other one the player might have summoned.
    private boolean isSunProducer(Zombie zombie) {
        models.game.gamemodes.IZombieMode mode =
                IZombieRenderer.modeOf(zombie.getGameSession());
        return mode != null && mode.isSunProducer(zombie);
    }

    // Called once per frame by GameRenderer: drops entities that were not drawn, and advances the
    // one-shot sequences. Advancing here rather than in clipFor is deliberate -- clipFor is called once
    // per zombie per frame, so ageing a sequence there would run it at several times its own speed.
    void sweepFlashes(float delta) {
        flashes.sweep();
        actions.advance(delta);
        actions.sweep();
    }

    // ---- foot planting ---------------------------------------------------------------------------

    // Built once per animation and clip. WalkCycle reads every frame of a part to find its path, which is
    // far too much work to repeat per zombie per frame; the answer only depends on the artwork.
    private final Map<String, WalkCycle> walks = new java.util.HashMap<>();
    // Clips already found to have no walk in them. null is a legitimate answer from WalkCycle.of, and
    // computeIfAbsent would retry the whole scan every frame for every idle zombie.
    private final java.util.Set<String> notWalks = new java.util.HashSet<>();

    // How far to shift the drawing so the feet stay planted, in world pixels. Zero for anything that is
    // not walking -- see WalkCycle.
    //
    // Drawing only. The model's x is untouched, so collisions, lane logic and everything the server would
    // compute are exactly as they were; the zombie simply travels between the same two points in its own
    // rhythm rather than at a constant glide.
    private float footPlanting(Zombie zombie, EntitySprite sprite, String clip, float stateTime) {
        WalkCycle walk = walkFor(zombie.getAlias(), sprite, clip);
        if (walk == null) {
            return 0f;
        }
        // One cycle's travel, in world pixels, from the MODEL: speed is cells per tick, so this follows a
        // chilled zombie down to half pace and a hypnotised one back the other way.
        double perTick = zombie.getMovement().getSpeed() * utils.Constants.ZOMBIE_SPEED_SCALE;
        float stride = (float) (perTick * utils.Constants.TICKS_PER_SECOND
                * sprite.clipDuration(clip)) * lawn.cellWidth();
        // Zombies walk right-to-left, so "ahead" is -x; a hypnotised one has turned round.
        float direction = zombie.getState().isHypnotized() ? 1f : -1f;
        float lead = walk.lead(stateTime);
        reportFootPlanting(zombie, sprite, clip, stateTime, lead, stride);
        return direction * stride * lead;
    }

    // -Dpvz.footCheck=1. See DebugFlags: the effect is motion, so the curve is what can be checked.
    private boolean footCheckDone;

    private void reportFootPlanting(Zombie zombie, EntitySprite sprite, String clip, float stateTime,
                                    float lead, float stride) {
        if (!views.gdx.core.DebugFlags.FOOT_CHECK || footCheckDone) {
            return;
        }
        float duration = sprite.clipDuration(clip);
        com.badlogic.gdx.Gdx.app.log("FootCheck", String.format(
                "%s [%s] phase %.3f  lead %+.3f  offset %+6.1f px  (stride %.1f px/cycle, %.2fs)",
                zombie.getAlias(), clip, duration <= 0f ? 0f : stateTime / duration, lead,
                -lead * stride, stride, duration));
        // One zombie only, and only while it is walking: twelve of them at 60 fps is a wall of text with
        // no more information in it than one has.
        footCheckDone = true;
    }

    // Cleared each frame by GameRenderer's sweep, so the log is one line per frame rather than one ever.
    void resetFootCheck() {
        footCheckDone = false;
    }

    private WalkCycle walkFor(String alias, EntitySprite sprite, String clip) {
        String key = alias + '#' + clip;
        WalkCycle cached = walks.get(key);
        if (cached != null || notWalks.contains(key)) {
            return cached;
        }
        WalkCycle walk = WalkCycle.of(sprite, clip);
        if (walk == null) {
            notWalks.add(key);
        } else {
            walks.put(key, walk);
        }
        return walk;
    }

    // Fractional lane -> foot line, so a lane switch slides instead of snapping.
    private float laneFootY(float lane) {
        int base = (int) Math.floor(lane);
        float frac = lane - base;
        float low = lawn.worldY(base);
        float high = lawn.worldY(Math.min(base + 1, utils.Constants.BOARD_ROWS - 1));
        return low + (high - low) * frac + lawn.cellHeight() * SpritePlacer.FOOT_INSET;
    }

    private static Color tintFor(Zombie zombie) {
        if (zombie.getState().isSubmerged()) {
            return SUBMERGED;
        }
        if (zombie.getState().isFrozen()) {
            return FROZEN;
        }
        if (zombie.getState().isHypnotized()) {
            return HYPNOTISED;
        }
        if (zombie.getState().isChilled()) {
            return CHILLED;
        }
        return Color.WHITE;
    }
}
