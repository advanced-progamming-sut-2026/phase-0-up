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
    private final ZombieFlight flight = new ZombieFlight();

    // Falling apart: which arm is still on, and the pieces already thrown. See ZombieDamage.
    private final views.gdx.sprite.ZombieDamage damage = new views.gdx.sprite.ZombieDamage();
    private final Dismemberment pieces;

    // Zombotany's plant heads. A no-op for every other zombie in the game.
    private final ZombotanyHead botany;

    // The armor each zombie was wearing last frame. A type that is on this map and no longer on the
    // stack was destroyed between two frames, and that transition IS the trigger for the fly-off --
    // the same "watch it rather than ask for an event" reasoning as DamageFlash. There is no armour
    // event in the model to listen for, and adding one would be a cosmetic concern in the tick loop.
    private final Map<Zombie, java.util.EnumSet<models.entities.zombies.Components.ArmorType>> worn =
            new java.util.IdentityHashMap<>();
    // Filled and diffed each frame instead of allocating an EnumSet per zombie per frame.
    private final java.util.EnumSet<models.entities.zombies.Components.ArmorType> scratchWorn =
            java.util.EnumSet.noneOf(models.entities.zombies.Components.ArmorType.class);

    public ZombieActions actions() {
        return actions;
    }

    // Drawn by GameRenderer with the rest of the lane, after the living.
    public Dismemberment pieces() {
        return pieces;
    }

    // The world this level is set in, for the one zombie whose art depends on it -- see spriteNameFor.
    private final models.game.EnvironmentType environment;

    public ZombieRenderer(SpriteRegistry sprites, LawnGeometry lawn, EntityInterpolator interpolator,
                          AnimationClocks clocks, models.game.EnvironmentType environment) {
        this.sprites = sprites;
        this.lawn = lawn;
        this.interpolator = interpolator;
        this.clocks = clocks;
        this.environment = environment;
        this.pieces = new Dismemberment(sprites);
        this.botany = new ZombotanyHead(sprites);
    }

    public ZombotanyHead botany() {
        return botany;
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
        // And a hop wins over both: a Dodo Rider clearing a Wall-nut is still WALKING as far as its
        // ActionState is concerned, which is how it used to walk straight through one. See ZombieFlight.
        String hop = flight.clipFor(zombie, sprite);
        String clip = hop != null ? hop
                : action != null ? action : ClipMap.forZombie(sprite, zombie);
        // Per zombie, restarted on clip change: otherwise the whole horde steps in unison and a
        // zombie that stops to bite starts its "eat" animation halfway through.
        float stateTime = ClipMap.sample(sprite, clip,
                clocks.advance(zombie, clip, heldStill(zombie) ? 0f : delta));

        float modelX = (float) zombie.getMovement().getPositionX();
        int modelLane = zombie.getMovement().getPositionY();

        float x = lawn.worldX(interpolator.x(zombie, modelX, alpha)) + footPlanting(zombie, sprite,
                clip, stateTime);
        float lane = interpolator.lane(zombie, modelLane, alpha);
        float footY = laneFootY(lane) + flightArc(zombie);
        reportLane(zombie, modelLane, lane, footY);

        // No armour map for the mech: its parts are its own, and a bucket toggle would name nothing.
        Map<String, Boolean> parts = isSunProducer(zombie)
                ? null : ArmorVisibility.forZombie(zombie, sprite);

        Color previous = batch.getColor().cpy();
        batch.setColor(tintFor(zombie));

        // Zombies walk right-to-left, so they face LEFT -- unless they are travelling the other way.
        // Two do: a hypnotised one, which has turned around for the player, and a Prospector that has
        // blown itself back down the lane on its own dynamite. Asked as "which way is it going" rather
        // than "is it charmed", because those are the same question only for one of the two.
        boolean faceRight = zombie.getMovement().isMovingRight();

        // Falling apart. Both of these have to happen BEFORE the draw, because losing the arm changes
        // the very visibility map the zombie is about to be drawn with.
        if (!isSunProducer(zombie)) {
            parts = shedPieces(zombie, sprite, parts, x, footY, Math.round(lane), faceRight);
        }
        // A Zombotany zombie loses its skull here rather than in ArmorVisibility: the head is not armour
        // and switching it off is a decision about WHICH zombie this is, not about what it is wearing.
        String alias = zombie.getAlias();
        parts = ZombotanyHead.hideSkull(alias, sprite, parts);

        drawIceBehind(batch, zombie, sprite, x, footY, delta, clip, stateTime);
        batch.setColor(tintFor(zombie));
        drawWhole(batch, alias, sprite, clip, stateTime, x, footY, faceRight, parts,
                scaleFor(zombie));

        // Carrier aura: the same frame again, additively, in the colour of whatever it is carrying.
        //
        // Drawn BEFORE the hit flash so a carrier being shot still reads as being shot -- the white
        // flash is the louder signal and has to win the frame it fires on.
        float aura = carrierPulse(zombie);
        if (aura > 0f) {
            Color colour = carrierColour(zombie);
            SpritePlacer.beginAdditive(batch);
            batch.setColor(colour.r * aura, colour.g * aura, colour.b * aura, 1f);
            drawWhole(batch, alias, sprite, clip, stateTime, x, footY, faceRight, parts,
                    scaleFor(zombie));
            SpritePlacer.endAdditive(batch);
        }

        // Hit flash: the same frame again, additively, so the zombie lights up white. Total HP, not the
        // body's -- a cone or a bucket absorbing a pea is still a hit, and the zombie should react.
        float flash = zombie.getHealth() == null ? 0f
                : flashes.intensity(zombie, zombie.getHealth().getTotalHP(), delta);
        if (flash > 0f) {
            SpritePlacer.beginAdditive(batch);
            batch.setColor(flash, flash, flash, 1f);
            drawWhole(batch, alias, sprite, clip, stateTime, x, footY, faceRight, parts,
                    scaleFor(zombie));
            SpritePlacer.endAdditive(batch);
        }

        // Front half of the block a frozen zombie is inside, over everything: the flash of a shot
        // landing on the ice belongs under it, not on top.
        drawIce(batch, zombie, ICE_BLOCK_FRONT, x, footY, delta, ICE_FRONT_ALPHA);

        batch.setColor(previous);
    }


    // ---- the ice block ---------------------------------------------------------------------------

    // A frozen zombie is drawn inside the game's own ice block, in the same two passes TerrainRenderer
    // uses for an authored '&': the rear half behind it, the front half over it at partial alpha.
    //
    // It used to get a blue tint and nothing else -- and the model has two quite different ways of
    // freezing a zombie. A block AUTHORED into the level is a FrozenTerrain, which TerrainRenderer has
    // always drawn; a zombie frozen in play (Iceberg Lettuce, an ice shot) sets a flag on the zombie and
    // adds no terrain at all, so the second kind was a slightly bluer zombie standing still. Same
    // state to the player, so the same art.
    private static final String ICE_BLOCK_FRONT = "FROSTBITE_ICE_BLOCK_ZOMBIE";
    private static final String ICE_BLOCK_BEHIND = "FROSTBITE_ICE_BLOCK_ZOMBIE_BEHIND";
    private static final String[] ICE_BLOCK_CLIPS = {"idle"};

    // How much of the zombie still reads through the front half. Matches TerrainRenderer's, and for the
    // same reason: this world is near-white and so is the block.
    private static final float ICE_FRONT_ALPHA = 0.45f;

    // How high a flying Prospector is above its lane, in world pixels.
    //
    // The model owns where the zombie is ALONG the lane -- CarryADynamite walks its x from the launch
    // tile to column 0 over thirteen ticks -- and this is the other axis, which the model has no notion
    // of and does not need one: height is pure presentation, and a lane is a line rather than a plane.
    //
    // A parabola through the progress, so it leaves the ground at the launch, peaks over the middle of
    // the lawn and touches down exactly as the flight ends. 4*p*(1-p) is zero at both ends and 1 in the
    // middle, which is precisely the shape wanted and costs one multiply.
    //
    // The progress comes from ZombieFlight's own clock rather than from the model's, so it advances
    // every FRAME instead of every tick -- see ZombieFlight.flightProgress. Reading the model's stepped
    // it ten times a second and the zombie climbed in stairs.
    private static final float FLIGHT_APEX_CELLS = 2.2f;

    private float flightArc(Zombie zombie) {
        float p = flight.flightProgress(zombie);
        if (p <= 0f) {
            return 0f;
        }
        return 4f * p * (1f - p) * FLIGHT_APEX_CELLS * lawn.cellHeight();
    }

    // Whether this zombie's animation should be standing still this frame.
    //
    // A frozen zombie's stops dead, and so does a BUTTERED one. The model already holds the x still for
    // both, but the walk clip kept playing -- so it marched on the spot and read as "the freeze does
    // nothing". The caller passes 0 delta instead, which holds the pose; the clock is still touched, so
    // AnimationClocks does not sweep the entry and restart the walk from frame 0 when it comes back.
    //
    // Butter is a stun, not a slow: Zombie.update returns above every ability while it lasts, so a
    // buttered zombie that went on swinging, tossing or chewing on screen would be showing work the
    // model is not doing. Whatever pose it was caught in is the honest one to hold.
    private static boolean heldStill(Zombie zombie) {
        return zombie.getState().isFrozen() || zombie.getState().isButtered();
    }

    // Everything that belongs UNDER the zombie: the rear half of the block a frozen one is inside, so
    // it is within the ice rather than behind a sticker, and whatever it is SHOVING, so its hands come
    // out onto the thing it is leaning on.
    private void drawIceBehind(Batch batch, Zombie zombie, EntitySprite sprite, float x, float footY,
                               float delta, String clip, float stateTime) {
        drawIce(batch, zombie, ICE_BLOCK_BEHIND, x, footY, delta, 1f);
        // Both pushers hang off the zombie's DRAWN x plus the shove, so the thing being pushed shares
        // its interpolation, its foot planting and the thrust of its arms. See pushShove.
        float front = x + pushShove(zombie, sprite, clip, stateTime);
        drawPushedIce(batch, zombie, front, footY, delta);
        drawArcadeCabinet(batch, zombie, front, footY, delta);
        drawPiano(batch, zombie, x, footY, delta);
    }

    // The Piano Zombie's piano, which is a whole separate animation and was never drawn.
    //
    // The dump ships two: ZOMBIE_PIANO is the zombies at the keyboard and PIANO is the instrument they
    // are pushing. The alias resolves to the first, so the lane got a pianist rolling along playing
    // nothing -- and the ability crushing two rows of plants had no visible cause at all.
    //
    // At the zombie's OWN x with no offset, unlike the ice and the cabinet: PianoCrushAbility flattens
    // what is within a tile of the zombie itself across its lane AND the one above, so the instrument
    // is not out in front of the group, it is what the group is standing at. Its authored height is
    // what covers the second row.
    //
    // Drawn UNDER the zombie so the players stay visible over their own instrument, and on `play`,
    // which is what a piano rolling down a lawn with someone sitting at it is doing.
    private static final String PIANO_SPRITE = "PIANO";
    private static final String[] PIANO_CLIPS = {"play", "idle"};
    // Battered but still rolling. The instrument takes the same fire the zombies pushing it do, so it
    // shows it: below half health it switches from playing to a splintered version of the same loop,
    // which is the piano's equivalent of a zombie losing an arm. Its `die` belongs to the moment it
    // comes apart and is played by DeathEffects, not here.
    private static final String[] PIANO_DAMAGED_CLIPS = {"damage", "play2", "play"};
    private static final float PIANO_DAMAGE_THRESHOLD = 0.5f;
    private static final String PIANO_ALIAS = "ZombiePiano";

    private void drawPiano(Batch batch, Zombie zombie, float x, float footY, float delta) {
        if (!PIANO_ALIAS.equalsIgnoreCase(zombie.getAlias())) {
            return;
        }
        EntitySprite piano = sprites.get(PIANO_SPRITE);
        if (piano == null || !piano.isReady()) {
            return;
        }
        String clip = ClipMap.firstAvailable(piano, pianoBattered(zombie)
                ? PIANO_DAMAGED_CLIPS : PIANO_CLIPS);
        float stateTime = ClipMap.sample(piano, clip,
                clocks.advance(iceKey(PIANO_SPRITE, zombie), clip, heldStill(zombie) ? 0f : delta));
        float previous = batch.getPackedColor();
        batch.setColor(1f, 1f, 1f, 1f);
        SpritePlacer.drawStanding(batch, piano, clip, stateTime, x, footY, true, null);
        batch.setPackedColor(previous);
    }

    // Measured against the health the zombie STARTED with, not the surviving layers: peeling armour
    // pops layers off the stack, so summing what is left can never recover the original. Same figure
    // ZombieDamage uses to decide when an arm comes off.
    private static boolean pianoBattered(Zombie zombie) {
        if (zombie.getHealth() == null) {
            return false;
        }
        return zombie.getHealth().getTotalHP()
                < zombie.getHealth().getMaxTotalHp() * PIANO_DAMAGE_THRESHOLD;
    }

    // How far ahead of the ZOMBIE the thing it is pushing should be drawn this frame, in world pixels,
    // on top of its resting offset.
    //
    // The pushed object used to sit at a fixed distance ahead: it travelled with the zombie's position
    // but not with its ANIMATION, so a Troglobite pumped its arms against a block gliding along
    // untouched. The cycle is now push, walk up, push -- the block is sent on ahead during the thrust
    // and stands still while the zombie closes the gap.
    //
    // Both terms are "how far ahead of the straight line", as fractions of one cycle's travel, and the
    // answer is the DIFFERENCE: the block's own progress (PushCycle, the running maximum of the pushing
    // hand) minus the body's (WalkCycle, the ground swatch). Subtracting the body is what makes the gap
    // open and close instead of the pair moving as one piece -- and it is also why the hold is a real
    // hold, because during the flat stretch the model's advance is cancelled exactly.
    //
    // Stride is computed the same way foot planting computes it, from the MODEL's speed, so a chilled
    // pusher shoves proportionally less and the two corrections stay in the same units. Direction
    // matches the facing for the same reason as well: a shove pointing the wrong way would pull the
    // block back through the zombie on every thrust.
    private float pushShove(Zombie zombie, EntitySprite sprite, String clip, float stateTime) {
        if (sprite == null || clip == null) {
            return 0f;
        }
        views.gdx.sprite.PushCycle push = pushFor(zombie.getAlias(), sprite, clip);
        if (push == null) {
            return 0f;
        }
        WalkCycle walk = walkFor(zombie.getAlias(), sprite, clip);
        float bodyLead = walk == null ? 0f : walk.lead(stateTime);
        double perTick = zombie.getMovement().getSpeed() * utils.Constants.ZOMBIE_SPEED_SCALE;
        float stride = (float) (perTick * utils.Constants.TICKS_PER_SECOND
                * sprite.clipDuration(clip)) * lawn.cellWidth();
        float direction = zombie.getMovement().isMovingRight() ? 1f : -1f;
        return direction * stride * (push.lead(stateTime) - bodyLead);
    }

    // Cached per (alias, clip) exactly as the walk cycles are: building one walks every frame of the
    // clip, and this is asked once per pushing zombie per frame.
    private final Map<String, views.gdx.sprite.PushCycle> pushes = new java.util.HashMap<>();
    private final java.util.Set<String> pushesMissing = new java.util.HashSet<>();

    private views.gdx.sprite.PushCycle pushFor(String alias, EntitySprite sprite, String clip) {
        String key = alias + '#' + clip;
        views.gdx.sprite.PushCycle cached = pushes.get(key);
        if (cached != null || pushesMissing.contains(key)) {
            return cached;
        }
        views.gdx.sprite.PushCycle built = views.gdx.sprite.PushCycle.of(sprite, clip);
        if (built == null) {
            pushesMissing.add(key);
        } else {
            pushes.put(key, built);
        }
        return built;
    }


    // The Arcade Zombie's machine, which had no art on the lawn at all.
    //
    // 80S_ARCADE_CABINET is its own animation rather than a part of the zombie -- in the original the
    // cabinet is a grid item the zombie happens to be behind -- so it is drawn as a separate object one
    // tile ahead, exactly as the Troglobite's ice blocks are, and BEFORE the zombie so the zombie's
    // hands come out onto it.
    //
    // `active` is the 1.7s attract-mode loop, which is what a working arcade machine does: it sits
    // there playing to nobody. `idle` is a quarter-second still and `death` belongs to the moment it
    // comes apart, which the model announces separately.
    private static final String ARCADE_CABINET = "80S_ARCADE_CABINET";
    private static final String[] ARCADE_CABINET_CLIPS = {"active", "idle"};
    private static final float ARCADE_OFFSET_CELLS = 0.9f;

    private void drawArcadeCabinet(Batch batch, Zombie zombie, float front, float footY, float delta) {
        if (!models.entities.zombies.Abilities.ArcadePushAbility.stillPushing(zombie)) {
            return;
        }
        EntitySprite cabinet = sprites.get(ARCADE_CABINET);
        if (cabinet == null || !cabinet.isReady()) {
            return;
        }
        String clip = ClipMap.firstAvailable(cabinet, ARCADE_CABINET_CLIPS);
        float stateTime = ClipMap.sample(cabinet, clip,
                clocks.advance(iceKey(ARCADE_CABINET, zombie), clip, delta));
        float offset = lawn.cellWidth() * ARCADE_OFFSET_CELLS;
        float cabinetX = zombie.getMovement().isMovingRight() ? front + offset : front - offset;

        float previous = batch.getPackedColor();
        batch.setColor(1f, 1f, 1f, 1f);
        SpritePlacer.drawStanding(batch, cabinet, clip, stateTime, cabinetX, footY, true, null);
        batch.setPackedColor(previous);
    }

    // The wall of ice a Troglobite pushes, which had no art at all.
    //
    // In the model it is a stack of ICE_BLOCK layers -- three of them, each holding a Yeti Imp -- and
    // the shared body's visibility maps cannot help: ZOMBIE_ICEAGE_TROGLOBITE carries no block part at
    // all. Its art is a zombie leaning forward with empty hands, because in the original the blocks are
    // separate objects. So they are drawn as separate objects, from the same two-part block that
    // TerrainRenderer and the frozen-zombie path already use.
    //
    // Drawn BEFORE the zombie, and that is the whole difference between shoving and hiding: painted
    // afterwards, the nearest block covers the Troglobite and it reads as a zombie cowering behind the
    // ice rather than leaning into it.
    //
    // Both halves of each block at full opacity, unlike an occupied one: the transparency exists so a
    // prisoner reads through the front, and the imp in these is not drawn until it is let out.
    private void drawPushedIce(Batch batch, Zombie zombie, float front, float footY, float delta) {
        int blocks = models.entities.zombies.Abilities.PushIceAbility.blocksLeft(zombie);
        // The model's own spacing, borrowed rather than re-guessed: it is what decides where the wall
        // actually crushes things, and a drawn wall a different width from the one doing the crushing
        // is a lie the player can measure.
        float step = lawn.cellWidth()
                * (float) models.entities.zombies.Abilities.PushIceAbility.BLOCK_SPACING;
        float direction = zombie.getMovement().isMovingRight() ? 1f : -1f;
        // Laid out from the zombie's drawn position rather than from its model tile. Reading the model
        // straight -- which is what this did -- meant the wall ignored the interpolation between ticks
        // and the foot planting, so it juddered against a zombie that was moving smoothly.
        //
        // Furthest first, so a nearer block overlaps the one ahead of it the way a row of solid objects
        // does when you are looking down the lane at it.
        for (int index = blocks - 1; index >= 0; index--) {
            drawIceBlockAt(batch, zombie, index, front + direction * step * (index + 1), footY, delta);
        }
    }

    // Each block is drawn in three passes, which is the whole reason the block art ships in halves: the
    // rear behind, the PASSENGER, then the front over it at partial alpha. Exactly how a frozen zombie
    // is drawn inside its own block -- and it is the same situation, because there really is a zombie
    // in there. Drawn at full opacity with nothing between the halves, the blocks were solid lumps and
    // the Yeti Imp that bursts out of one arrived from nowhere.
    private void drawIceBlockAt(Batch batch, Zombie zombie, int index, float blockX, float footY,
                                float delta) {
        drawIceHalf(batch, zombie, ICE_BLOCK_BEHIND, index, blockX, footY, delta, 1f);
        drawTrappedImp(batch, zombie, index, blockX, footY, delta);
        drawIceHalf(batch, zombie, ICE_BLOCK_FRONT, index, blockX, footY, delta, ICE_FRONT_ALPHA);
    }

    private void drawIceHalf(Batch batch, Zombie zombie, String half, int index, float blockX,
                             float footY, float delta, float alpha) {
        EntitySprite ice = sprites.get(half);
        if (ice == null || !ice.isReady()) {
            return;
        }
        String clip = ClipMap.firstAvailable(ice, ICE_BLOCK_CLIPS);
        // Keyed per block as well as per zombie, or the three would shimmer in lockstep and read as
        // one block drawn three times.
        float stateTime = ClipMap.sample(ice, clip,
                clocks.advance(iceKey(half + index, zombie), clip, delta));
        float previous = batch.getPackedColor();
        batch.setColor(1f, 1f, 1f, alpha);
        SpritePlacer.drawStanding(batch, ice, clip, stateTime, blockX, footY, true, null);
        batch.setPackedColor(previous);
    }

    // The Yeti Imp waiting inside one of the blocks.
    //
    // Not a model object -- the imp does not exist until the block breaks and PushIceAbility creates it
    // -- so this is the view drawing what the block CONTAINS, which the model records only as "one more
    // ICE_BLOCK layer". That is enough: every one of those layers is an imp's ride.
    //
    // Its own art in this world, and its `idle` clip, held rather than run: something frozen in a block
    // is not walking. Clocked at zero delta for exactly that reason.
    private void drawTrappedImp(Batch batch, Zombie zombie, int index, float blockX, float footY,
                                float delta) {
        EntitySprite imp = sprites.get(ICE_IMP_SPRITE);
        if (imp == null || !imp.isReady()) {
            return;
        }
        String clip = ClipMap.firstAvailable(imp, ClipMap.IDLE);
        float stateTime = ClipMap.sample(imp, clip,
                clocks.advance(iceKey("imp" + index, zombie), clip, 0f));
        float previous = batch.getPackedColor();
        batch.setColor(1f, 1f, 1f, 1f);
        SpritePlacer.drawStanding(batch, imp, clip, stateTime, blockX, footY, true, null);
        batch.setPackedColor(previous);
    }

    private void drawIce(Batch batch, Zombie zombie, String spriteName, float x, float footY,
                         float delta, float alpha) {
        if (!zombie.getState().isFrozen()) {
            return;
        }
        EntitySprite ice = sprites.get(spriteName);
        if (ice == null || !ice.isReady()) {
            return;   // the tint still says "frozen"
        }
        String clip = ClipMap.firstAvailable(ice, ICE_BLOCK_CLIPS);
        // Its own clock key, not the zombie's: the zombie's clock is holding its body pose at a
        // standstill (animationDelta is 0 while frozen), and the block should still shimmer.
        float stateTime = ClipMap.sample(ice, clip, clocks.advance(iceKey(spriteName, zombie),
                clip, delta));

        float previous = batch.getPackedColor();
        batch.setColor(1f, 1f, 1f, alpha);
        SpritePlacer.drawStanding(batch, ice, clip, stateTime, x, footY, true, null);
        batch.setPackedColor(previous);
    }

    // Interned per (art, zombie), because AnimationClocks is keyed by identity and a string built fresh
    // each frame is a new clock each frame -- which pins the block to frame 0.
    private final Map<Zombie, Map<String, String>> iceKeys = new java.util.IdentityHashMap<>();

    private Object iceKey(String spriteName, Zombie zombie) {
        return iceKeys.computeIfAbsent(zombie, z -> new java.util.HashMap<>())
                .computeIfAbsent(spriteName, name -> name + "#"
                        + Integer.toHexString(System.identityHashCode(zombie)));
    }
    // Body, then whatever stands in for its head. One method because the hit flash redraws the SAME
    // frame additively, and a plant-headed zombie that lit up only from the neck down would read as the
    // head belonging to something else.
    private void drawWhole(Batch batch, String alias, EntitySprite sprite, String clip,
                           float stateTime, float x, float footY, boolean faceRight,
                           Map<String, Boolean> parts, float scale) {
        // faceRight IS the mirror here: zombie art is authored walking left, so anything travelling
        // the other way is drawn flipped. See SpritePlacer.drawStandingScaled for why this cannot go
        // through the EntitySprite flag.
        SpritePlacer.drawStandingScaled(batch, sprite, clip, stateTime, x, footY, faceRight, parts,
                scale);
        botany.draw(batch, alias, sprite, clip, stateTime, x, footY, faceRight);
    }

    // -Dpvz.laneCheck=1. See DebugFlags: a tall sprite covering the rows above its feet is
    // indistinguishable by eye from one standing in the wrong row, so the numbers are what can be
    // checked. `drawn` differing from `model` by anything but a fraction mid-lane-switch is the bug.
    private void reportLane(Zombie zombie, int modelLane, float drawnLane, float footY) {
        if (!views.gdx.core.DebugFlags.LANE_CHECK) {
            return;
        }
        boolean off = Math.abs(drawnLane - modelLane) > 0.01f;
        com.badlogic.gdx.Gdx.app.log("LaneCheck", String.format(java.util.Locale.ROOT,
                "%-22s model lane %d  drawn lane %.3f  footY %.1f (lane %d foot line is %.1f)%s",
                zombie.getAlias(), modelLane, drawnLane, footY, modelLane,
                laneFootY(modelLane), off ? "   <-- OFF" : ""));
    }

    // The disco mech is authored about half again the size of a browncoat -- fine for the one-off boss
    // it is in the real game, wrong for five of them parked in a column, where each overlapped the lane
    // above and below. Brought down to roughly a normal zombie's footprint.
    private static final float SUN_PRODUCER_SCALE = 0.62f;

    // The Yeti Imp, which is an ordinary Imp that came out of a block of ice.
    //
    // This roster has one imp -- ZombieImp, drawn in its Dark Ages monk's robes -- and the game calls
    // the one a Troglobite carries a Yeti Imp; zombies.json even names the type ("iceage_imp"). Rather
    // than invent a second zombie for what is the same 190-HP imp with the same behaviour, the WORLD
    // picks the coat, which is exactly what SUN_PRODUCER_SPRITE below does for a different reason. A
    // monk in a fur cave was the only thing wrong with it.
    private static final String ICE_IMP_SPRITE = "ZOMBIE_ICEAGE_IMP";
    private static final String IMP_ALIAS = "ZombieImp";

    // A peasant the King Zombie has knighted, drawn as THE Knight Zombie.
    //
    // A knighted peasant and ZombieDarkArmor3 are the same thing -- zombies.json gives the Knight
    // Zombie exactly the pair of armours the king hands out (CrownDefault, ShoulderArmorDefault) -- so
    // it is drawn from the same art, and the two are guaranteed to look alike because they ARE alike.
    // SpriteRegistry keys its still images on the alias, so this inherits the Knight's picture and its
    // scale with nothing further to keep in step.
    //
    // The alternative was the Dark Ages peasant body with its crown and pauldron parts switched on,
    // which would animate. It is not used, twice over: the shared body a peasant is normally drawn from
    // (ZOMBIE_TUTORIAL) has no such parts at all, which is why a knighted zombie was coming out
    // bare-headed -- and the substitute body did not read as a knight either. Whether those parts can
    // be made to show is not answerable from the asset dump alone; the Knight's own art needs no such
    // question asked of it.
    private static final String KNIGHT_ALIAS = "ZombieDarkArmor3";
    private static final String PEASANT_ALIAS = "ZombieDefault";

    private String spriteNameFor(Zombie zombie) {
        if (isSunProducer(zombie)) {
            return SUN_PRODUCER_SPRITE;
        }
        if (PEASANT_ALIAS.equalsIgnoreCase(zombie.getAlias()) && isKnighted(zombie)) {
            return KNIGHT_ALIAS;
        }
        if (IMP_ALIAS.equalsIgnoreCase(zombie.getAlias())
                && environment == models.game.EnvironmentType.FROSTBITE_CAVES) {
            return ICE_IMP_SPRITE;
        }
        return zombie.getAlias();
    }

    // Whether this zombie is carrying a knighthood -- either piece is enough, because a knight that has
    // had its helm shot off is still wearing the pauldron and still needs the body that can draw it.
    private static boolean isKnighted(Zombie zombie) {
        if (zombie.getHealth() == null) {
            return false;
        }
        for (models.entities.zombies.Components.HealthLayer layer : zombie.getHealth().getLayers()) {
            models.entities.zombies.Components.ArmorType type = layer.getType();
            if (type == models.entities.zombies.Components.ArmorType.CROWN
                    || type == models.entities.zombies.Components.ArmorType.SHOULDER_ARMOR) {
                return true;
            }
        }
        return false;
    }

    private float scaleFor(Zombie zombie) {
        return isSunProducer(zombie) ? SUN_PRODUCER_SCALE : 1f;
    }

    // Asked of the MODE, which is the only thing that knows which five of the board's zombies it
    // designated as makers -- they are otherwise ordinary bucketheads and indistinguishable from any
    // other one the player might have summoned.
    private boolean isSunProducer(Zombie zombie) {
        models.game.gamemodes.BrainLawn mode =
                IZombieRenderer.modeOf(zombie.getGameSession());
        return mode != null && mode.isSunProducer(zombie);
    }

    // Called once per frame by GameRenderer: drops entities that were not drawn, and advances the
    // one-shot sequences. Advancing here rather than in clipFor is deliberate -- clipFor is called once
    // per zombie per frame, so ageing a sequence there would run it at several times its own speed.
    void sweepFlashes(float delta) {
        auraClock += delta;
        flashes.sweep();
        actions.advance(delta);
        actions.sweep();
        flight.advance(delta);
        flight.sweep();
        damage.sweep();
        pieces.advance(delta);
        worn.keySet().removeIf(zombie -> zombie.getHealth() == null
                || zombie.getHealth().getTotalHP() <= 0);
        // Same rule for the ice-block clock keys: a zombie that is gone cannot be frozen.
        iceKeys.keySet().removeIf(zombie -> zombie.getHealth() == null
                || zombie.getHealth().getTotalHP() <= 0);
    }

    // Everything a zombie loses on its way to dying: the armour that has just been destroyed, and the
    // arm it sheds once its body is half gone. Returns the visibility map to draw it with.
    //
    // Both are watched rather than announced. The model raises no event for a cone breaking or for a
    // body crossing a health fraction, and it should not: both are cosmetic, and either would fire
    // dozens of times a second during a heavy wave. A layer leaving the stack IS the cone breaking,
    // exactly as a projectile vanishing is the impact.
    private Map<String, Boolean> shedPieces(Zombie zombie, EntitySprite sprite,
                                            Map<String, Boolean> parts, float x, float footY,
                                            int row, boolean faceRight) {
        scratchWorn.clear();
        for (models.entities.zombies.Components.HealthLayer layer : zombie.getHealth().getLayers()) {
            if (layer.getType() != null) {
                scratchWorn.add(layer.getType());
            }
        }
        java.util.EnumSet<models.entities.zombies.Components.ArmorType> before = worn.get(zombie);
        if (before == null) {
            worn.put(zombie, java.util.EnumSet.copyOf(scratchWorn));
        } else {
            for (models.entities.zombies.Components.ArmorType type : before) {
                if (!scratchWorn.contains(type)) {
                    pieces.throwArmor(sprite, spriteNameFor(zombie),
                            ArmorVisibility.destroyedPartName(type), x, footY, row, faceRight);
                }
            }
            before.clear();
            before.addAll(scratchWorn);
        }

        // Asked exactly once: armState CONSUMES the rising edge, so a second call this frame would
        // answer LOST and the arm would never be thrown.
        views.gdx.sprite.ZombieDamage.ArmState arm = damage.armState(zombie);
        if (arm == views.gdx.sprite.ZombieDamage.ArmState.INTACT) {
            return parts;
        }
        if (arm == views.gdx.sprite.ZombieDamage.ArmState.JUST_LOST) {
            pieces.throwArm(sprite, spriteNameFor(zombie), x, footY, row, faceRight);
        }
        return views.gdx.sprite.ZombieDamage.applyArmLoss(sprite, parts);
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
        // Zombies walk right-to-left, so "ahead" is -x; anything travelling the other way has turned
        // round. Same question the facing asks, and it has to be the same answer -- a sprite mirrored
        // one way with its feet planted the other slides backwards through its own stride.
        float direction = zombie.getMovement().isMovingRight() ? 1f : -1f;
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

    // ---- carrier aura ---------------------------------------------------------------------------
    //
    // A zombie carrying something is worth killing FIRST, and until now nothing on the board said so:
    // a glowing zombie and a plain one were the same sprite, and the player found out what it was
    // holding only after it was dead. So a carrier is lit from within, additively, on the same
    // mechanism the hit flash uses -- one extra draw of the frame already computed, which costs
    // nothing and cannot get out of step with the pose.
    //
    // Additive rather than a tint because a tint has to fight the status colours: a chilled zombie is
    // already blue and a hypnotised one already purple, and multiplying a second colour over either
    // produces a third that means neither. Light ADDS on top of whatever the zombie already is.

    // Pulse rate, in cycles per second. Slow enough to read as breathing rather than as a strobe, and
    // deliberately different from DangerGlow's 2 Hz so the two warnings are not mistaken for the same
    // thing when a carrier walks into the danger zone.
    private static final float PULSE_HZ = 1.35f;
    // The sine never reaches zero: an aura that switches off completely reads as a flicker, and for
    // half of every cycle the zombie would look ordinary.
    private static final float PULSE_FLOOR = 0.34f;
    private static final float PULSE_PEAK = 0.85f;

    // Plant food is the game's own green; the three loot drops wear the colours their icons already
    // have in the wallet and the shop, so the aura and the thing it promises match.
    private static final Color AURA_PLANT_FOOD = new Color(0.35f, 1f, 0.30f, 1f);
    private static final Color AURA_COIN = new Color(1f, 0.82f, 0.25f, 1f);
    private static final Color AURA_GEM = new Color(0.45f, 0.75f, 1f, 1f);
    private static final Color AURA_POT = new Color(1f, 0.55f, 0.25f, 1f);

    // Clocked off the renderer's own accumulated time, not off Gdx's wall clock: a paused board must
    // stop pulsing along with everything else, and the frame delta is already threaded here for the
    // damage flash.
    private float auraClock;

    // Advanced once per frame, in sweepFlashes, never inside carrierPulse -- the lane pass runs five
    // times a frame and would drive the pulse at five times its own speed.

    private float carrierPulse(Zombie zombie) {
        if (zombie == null || !zombie.carriesSomething()) {
            return 0f;
        }
        float wave = (float) Math.sin(auraClock * PULSE_HZ * 2f * Math.PI) * 0.5f + 0.5f;
        return PULSE_FLOOR + (PULSE_PEAK - PULSE_FLOOR) * wave;
    }

    // Plant food wins when a zombie carries both, because it is the one the player can spend right now.
    private static Color carrierColour(Zombie zombie) {
        if (zombie.isGlowing()) {
            return AURA_PLANT_FOOD;
        }
        models.entities.collectibles.Collectibles carried = zombie.getCarriedDrop();
        if (carried == null) {
            return AURA_PLANT_FOOD;
        }
        switch (carried) {
            case GEM:
                return AURA_GEM;
            case POT:
                return AURA_POT;
            default:
                return AURA_COIN;
        }
    }
}
