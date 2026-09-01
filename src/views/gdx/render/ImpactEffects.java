package views.gdx.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import views.gdx.sprite.ClipMap;
import views.gdx.sprite.EntitySprite;
import views.gdx.sprite.SpriteRegistry;

import java.util.ArrayList;
import java.util.List;

// Short bursts where a shot lands.
//
// The model has no notion of an impact -- Projectile.onHit applies damage and sets isDestroyed, and
// the object is swept the same tick -- so there is no event to listen to. The view infers it instead:
// GameRenderer notices a projectile that was in flight last frame and is gone this frame, and asks
// for a burst at its last drawn position. That covers zombie hits, grave hits and shots that expire
// on the board edge alike, without the model growing an "impact happened" flag it does not need.
//
// The burst is the game's own splat ANIMATION (T_SPLAT_PEA_ROCK), played once. Earlier versions drew a
// ring of generated discs, then a ring of scattered copies of one static splat frame; both were
// stand-ins for an animation that was in the dump all along.
public final class ImpactEffects {

    // One splat animation per element. These live under 768/INITIAL/EFFECTS/, not 768/FULL/ -- a search
    // of FULL alone finds only T_SPLAT_PEA_ROCK and concludes, wrongly, that ice and fire have no splat
    // of their own. Both trees have to be searched.
    private static final java.util.Map<models.entities.projectiles.Element, String> SPLAT_BY_ELEMENT =
            new java.util.EnumMap<>(models.entities.projectiles.Element.class);

    private static final String SPLAT_DEFAULT = "T_SPLAT_PEA";

    static {
        SPLAT_BY_ELEMENT.put(models.entities.projectiles.Element.NEUTRAL, "T_SPLAT_PEA");
        SPLAT_BY_ELEMENT.put(models.entities.projectiles.Element.ICE, "T_SPLAT_SNOW_PEA");
        SPLAT_BY_ELEMENT.put(models.entities.projectiles.Element.FIRE, "T_SPLAT_FIRE_PEA");
    }

    // Shots whose splat belongs to the PLANT rather than to an element.
    //
    // Checked before the element map, because element is the wrong axis for these: a rutabaga and a
    // corn kernel are both NEUTRAL, and both were bursting into a pea splat. Kernel-pult even ships two
    // -- the kernel scatters, the butter splatters -- and the model already distinguishes them, so the
    // view can too.
    private static final java.util.Map<models.entities.projectiles.ProjectileType, String>
            SPLAT_BY_TYPE = new java.util.EnumMap<>(models.entities.projectiles.ProjectileType.class);

    static {
        SPLAT_BY_TYPE.put(models.entities.projectiles.ProjectileType.RUTABAGA,
                "ROTORUTABAGA_PROJECTILE_HIT");
        SPLAT_BY_TYPE.put(models.entities.projectiles.ProjectileType.CORN_KERNEL,
                "SPLAT_KERNALPULT_KERNAL");
        SPLAT_BY_TYPE.put(models.entities.projectiles.ProjectileType.BUTTER,
                "SPLAT_KERNALPULT_BUTTER");
        // Each lobbed fruit bursts as itself. A cabbage that leaves a pea splat is the same lie as a
        // Cabbage-pult throwing a pea, one frame later.
        SPLAT_BY_TYPE.put(models.entities.projectiles.ProjectileType.CABBAGE,
                "SPLAT_CABBAGEPULT");
        SPLAT_BY_TYPE.put(models.entities.projectiles.ProjectileType.MELON,
                "T_SPLAT_MELONPULT");
        SPLAT_BY_TYPE.put(models.entities.projectiles.ProjectileType.WINTER_MELON,
                "T_SPLAT_WINTERMELON");
        SPLAT_BY_TYPE.put(models.entities.projectiles.ProjectileType.PEPPER,
                "T_PEPPERPULT_PROJECTILE_SPLAT");
        SPLAT_BY_TYPE.put(models.entities.projectiles.ProjectileType.THORN,
                "CACTUS_PROJECTILE_HIT");
        SPLAT_BY_TYPE.put(models.entities.projectiles.ProjectileType.STAR,
                "T_STARFRUIT_PROJECTILE_HIT");
        // The boosted Cactus spike bursts as a cactus spike, not as Red Stinger's.
        SPLAT_BY_TYPE.put(models.entities.projectiles.ProjectileType.PIERCING_SPIKE,
                "CACTUS_PROJECTILE_HIT");
        SPLAT_BY_TYPE.put(models.entities.projectiles.ProjectileType.FUME,
                "FUMESHROOM_BUBBLES_HIT");
        // Citron bursts as citrus, and its plant-food orb bursts as plasma.
        SPLAT_BY_TYPE.put(models.entities.projectiles.ProjectileType.CITRUS_ORB,
                "CITRON_CITRUS_ORB_HIT");
        SPLAT_BY_TYPE.put(models.entities.projectiles.ProjectileType.CITRUS_PLASMA_ORB,
                "CITRON_PLANTFOOD_ORB_HIT");
    }

    // How wide each splat is drawn. A melon bursting is a much bigger event than a thorn landing, and
    // one size for all of them made the fruit splats look like specks.
    private static final java.util.Map<String, Float> SPLAT_WIDTH_BY_SPRITE = java.util.Map.of(
            "SPLAT_CABBAGEPULT", 0.75f,
            "T_SPLAT_MELONPULT", 0.95f,
            "T_SPLAT_WINTERMELON", 0.95f,
            "T_PEPPERPULT_PROJECTILE_SPLAT", 0.80f);

    // Clip variants, so repeated hits do not look stamped from one mould. T_SPLAT_PEA ships six;
    // firstAvailable falls back for the splats that ship fewer.
    private static final String[] CLIPS = {"animation", "animation2", "animation3"};

    // The splat art is authored far larger than a pea impact needs (its box is ~240 PAM units wide),
    // so it is scaled down to roughly a third of a tile.
    private static final float SPLAT_WIDTH_CELLS = 0.34f;

    private static final float LIFETIME = 0.30f;

    // A muzzle flash is drawn about half a tile across -- larger than a splat, because it is the plant's
    // own effect rather than a mark left on a zombie.
    private static final float MUZZLE_WIDTH_CELLS = 0.55f;

    // A strike travels from the plant to whatever it hit, so it needs longer than a splat and its own
    // size: the cloud and the dirt are plant-sized effects, not marks left on a zombie.
    private static final float STRIKE_WIDTH_CELLS = 0.9f;
    // Matched to GlobalTargetingAbility.WIND_UP_TICKS (5 ticks at 10 Hz). The effect has to arrive on
    // the frame the damage lands, so the two are the same half-second by construction rather than by
    // coincidence -- change one and the other has to move with it.
    private static final float STRIKE_LIFETIME = 0.5f;

    private static final class Burst implements com.badlogic.gdx.utils.Pool.Poolable {
        float x;
        float y;
        // Where it ends up. Equal to x/y for the ones that stay put, which is most of them.
        float toX;
        float toY;
        float age;
        float lifetime;
        float widthCells;
        String clip;
        String sprite;
        // A beam is BURNING for its whole life, not dissolving from the moment it appears. Without this
        // the laser would be at its brightest while the skull was still winding up and nearly gone by
        // the frame it actually kills anything, which is the fade running backwards.
        boolean sustained;
        // Owned by the Burst and set in place, never reassigned: a recycled burst must not drag a
        // fresh Color allocation along with it, which was the point of pooling it at all.
        final Color color = new Color();

        @Override
        public void reset() {
            x = 0f;
            y = 0f;
            toX = 0f;
            toY = 0f;
            age = 0f;
            lifetime = 0f;
            widthCells = 0f;
            clip = null;
            sprite = null;
            sustained = false;
            color.set(Color.WHITE);
        }
    }

    // One burst per shot that lands, which on a busy board is a dozen a second for the length of a
    // level. Recycled rather than allocated -- this is the hottest effect in the game and the reason
    // the blueprint asked for a pool.
    private final com.badlogic.gdx.utils.Pool<Burst> pool = new com.badlogic.gdx.utils.Pool<>() {
        @Override
        protected Burst newObject() {
            return new Burst();
        }
    };

    // The Ice Age Hunter's snowball, which is the one impact in the game the view cannot infer.
    //
    // Everything else here is deduced from the board -- a projectile that was in flight and is gone has
    // landed. The Hunter's throw creates no projectile at all: ThrowIceAbility reaches down the lane and
    // calls takeIceHit() on the plant, so from the view's side a plant simply turns blue with nothing
    // having crossed the gap. The model narrates it, so that sentence is the impact.
    private static final java.util.regex.Pattern ICE_THROWN = java.util.regex.Pattern.compile(
            "^.+? hurls ice at .+? at \\((\\d+), (\\d+)\\)\\.$");
    private static final String SNOWBALL_SPLAT = "ZOMBIE_HUNTER_SNOWBALL_SPLAT";
    // Bigger than a pea splat: this one covers a whole plant rather than marking a spot on a zombie.
    private static final float SNOWBALL_WIDTH_CELLS = 1.0f;
    private static final float SNOWBALL_LIFETIME = 0.625f;   // the clip's own length

    // And the octopus a Beach Zombie throws, which is the same problem again with one difference: this
    // one TRAVELS. The model binds the octopus to the plant in a single call with nothing in flight, so
    // the sentence carries both tiles and the view flies the art between them.
    private static final java.util.regex.Pattern OCTOPUS_THROWN = java.util.regex.Pattern.compile(
            "^.+? flings an octopus from \\((\\d+), (\\d+)\\) onto .+? at \\((\\d+), (\\d+)\\)\\.$");
    private static final String OCTOPUS = "ZOMBIE_OCTOPUS_PROJECTILE";
    // `animation` is the 0.3s throw -- the octopus balled up in the air. Its long clips are the clinging
    // loop PlantOctopus draws once it has landed, which is the wrong pose for something mid-flight.
    private static final String OCTOPUS_CLIP = "animation";
    private static final float OCTOPUS_WIDTH_CELLS = 0.8f;

    // The throw leaves the zombie's HAND, which is over its head, not its tile centre.
    //
    // The sentence carries tiles and a tile has no height, so both ends of the flight would otherwise
    // sit on the middle of a square: an octopus sliding along the ground out of the zombie's knees. A
    // zombie is about 1.3 cells tall from the foot line, so a hand raised to throw is a little under a
    // cell above the tile's centre.
    // The Arcade Zombie's machine coming apart, on the tile it was being shoved along.
    //
    // 80S_ARCADE_CABINET_BREAK had never been used: the machine could not be destroyed at all before,
    // so nothing had a moment to draw it at. Like the snowball, this is an impact the view cannot infer
    // -- there is no projectile, only a layer leaving a health stack -- so the model narrates it.
    private static final java.util.regex.Pattern ARCADE_BROKEN = java.util.regex.Pattern.compile(
            "^.+?'s arcade machine falls apart at \\((\\d+), (\\d+)\\).*$");
    private static final String ARCADE_BREAK = "80S_ARCADE_CABINET_BREAK";
    private static final float ARCADE_BREAK_WIDTH_CELLS = 1.2f;
    private static final float ARCADE_BREAK_LIFETIME = 1.07f;   // the clip's own length

    // The Turquoise's beam, laid down the four tiles it burns.
    //
    // A whole lane-clearing attack that had nothing on screen at all: plants in four tiles simply died
    // at once, with the skull standing there. The dump ships the beam as its own animation with one
    // clip, so it is drawn as one wide effect spanning exactly the tiles LaserBeamAbility damages --
    // drawn at the tiles' own count, so the picture and the rule cannot disagree about the reach.
    private static final java.util.regex.Pattern LASER_AIMED = java.util.regex.Pattern.compile(
            "^.+? levels its skull at \\((\\d+), (\\d+)\\) and takes aim down the next (\\d+) tiles\\.$");
    private static final String BEAM = "CRYSTALSKULL_BEAM";
    private static final String BEAM_CLIP = "laser_beam";
    // Raised on the AIM and held for the whole of it, so the beam is lit through the `attack` clip and
    // is still burning on the frame the plants die -- rather than appearing for two thirds of a second
    // after everything in the lane was already gone.
    //
    // 2.1s covers LaserBeamAbility's 20-tick aim with a beat to spare. `laser_beam` is 0.667s and
    // loops, so it runs three times over that; ClipMap.sample wraps it.
    private static final float BEAM_LIFETIME = 2.1f;
    // Lifted off the tile centre: the beam comes out of the skull's eyes, not its feet. Kept low
    // enough that it crosses the plants it is burning rather than passing over their heads.
    private static final float BEAM_LIFT_CELLS = 0.25f;

    // The Prospector firing itself down the lane: a bang where the dynamite went off, and a trail of
    // smoke arcing across to where it comes down.
    //
    // Two effects from one sentence, which is unusual here and is what the event describes: the model
    // moves the zombie in a single call, so nothing about the board records that anything crossed the
    // lawn. The blast stays on the tile it left; the smoke is the only thing that travels.
    private static final java.util.regex.Pattern PROSPECTOR_BLAST = java.util.regex.Pattern.compile(
            "^Boom! .+?'s dynamite explodes at \\((\\d+), (\\d+)\\) and blasts it back to "
                    + "\\((\\d+), (\\d+)\\)\\.$");
    private static final String PROSPECTOR_BLAST_OFF = "ZOMBIE_PROSPECTOR_BLAST_OFF";
    private static final String PROSPECTOR_SMOKE = "ZOMBIE_PROSPECTOR_SMOKE_ARC";
    private static final float BLAST_OFF_WIDTH_CELLS = 1.3f;
    private static final float BLAST_OFF_LIFETIME = 1.667f;   // the clip's own length
    private static final float SMOKE_WIDTH_CELLS = 1.0f;
    // Matched to CarryADynamite.FLIGHT_TICKS (13 at 10 Hz): the smoke trails the zombie for exactly as
    // long as the zombie is in the air, so the trail ends where the landing does.
    private static final float SMOKE_LIFETIME = 1.3f;
    // The smoke leaves at chest height rather than off the ground.
    private static final float SMOKE_LIFT_CELLS = 0.5f;

    private static final float OCTOPUS_FROM_LIFT_CELLS = 0.8f;
    // And it lands where the octopus is DRAWN once it has hold of the plant, not on the plant's feet --
    // the same lift PlantOctopus uses, so the thrown one arrives exactly where the clinging one appears.
    private static final float OCTOPUS_TO_LIFT_CELLS = 0.42f;

    private final SpriteRegistry sprites;
    private final views.gdx.map.LawnGeometry lawn;
    private final List<Burst> bursts = new ArrayList<>();
    private final LocalTransform transform = new LocalTransform();
    private int nextClip;

    public ImpactEffects(SpriteRegistry sprites, views.gdx.map.LawnGeometry lawn) {
        this.sprites = sprites;
        this.lawn = lawn;
    }

    // The Prospector's launch: two effects from one sentence, which is why it is not inline above.
    private boolean prospectorBlast(String text) {
        java.util.regex.Matcher blast = PROSPECTOR_BLAST.matcher(text);
        if (!blast.matches()) {
            return false;
        }
        float fromX = lawn.centerX(Integer.parseInt(blast.group(1)));
        float fromY = lawn.centerY(Integer.parseInt(blast.group(2)));
        float toX = lawn.centerX(Integer.parseInt(blast.group(3)));
        float toY = lawn.centerY(Integer.parseInt(blast.group(4)));
        float lift = lawn.cellHeight() * SMOKE_LIFT_CELLS;
        add(fromX, fromY, fromX, fromY, PROSPECTOR_BLAST_OFF,
                BLAST_OFF_WIDTH_CELLS, BLAST_OFF_LIFETIME, "animation");
        add(fromX, fromY + lift, toX, toY + lift, PROSPECTOR_SMOKE,
                SMOKE_WIDTH_CELLS, SMOKE_LIFETIME, "animation2");
        return true;
    }

    // Offered every event the model drains. Anything that is not a snowball landing is ignored.
    public void onEvent(String message) {
        if (message == null || lawn == null) {
            return;
        }
        String text = message.trim();
        try {
            java.util.regex.Matcher iced = ICE_THROWN.matcher(text);
            if (iced.matches()) {
                int col = Integer.parseInt(iced.group(1));
                int row = Integer.parseInt(iced.group(2));
                // On the plant, not on the tile: a splat centred on the square lands at its feet.
                add(lawn.centerX(col), lawn.centerY(row), lawn.centerX(col), lawn.centerY(row),
                        SNOWBALL_SPLAT, SNOWBALL_WIDTH_CELLS, SNOWBALL_LIFETIME, null);
                return;
            }
            java.util.regex.Matcher beam = LASER_AIMED.matcher(text);
            if (beam.matches()) {
                int col = Integer.parseInt(beam.group(1));
                int row = Integer.parseInt(beam.group(2));
                int reach = Integer.parseInt(beam.group(3));
                // Centred on the middle of the burnt stretch, which runs from the skull's own tile
                // back `reach` tiles toward the house.
                float centreX = lawn.centerX(col) - lawn.cellWidth() * (reach / 2f);
                float y = lawn.centerY(row) + lawn.cellHeight() * BEAM_LIFT_CELLS;
                add(centreX, y, centreX, y, BEAM, reach, BEAM_LIFETIME, BEAM_CLIP).sustained = true;
                return;
            }
            if (prospectorBlast(text)) {
                return;
            }
            java.util.regex.Matcher arcade = ARCADE_BROKEN.matcher(text);
            if (arcade.matches()) {
                int col = Integer.parseInt(arcade.group(1));
                int row = Integer.parseInt(arcade.group(2));
                add(lawn.centerX(col), lawn.centerY(row), lawn.centerX(col), lawn.centerY(row),
                        ARCADE_BREAK, ARCADE_BREAK_WIDTH_CELLS, ARCADE_BREAK_LIFETIME, null);
                return;
            }
            java.util.regex.Matcher octopus = OCTOPUS_THROWN.matcher(text);
            if (octopus.matches()) {
                float cell = lawn.cellHeight();
                add(lawn.centerX(Integer.parseInt(octopus.group(1))),
                        lawn.centerY(Integer.parseInt(octopus.group(2)))
                                + cell * OCTOPUS_FROM_LIFT_CELLS,
                        lawn.centerX(Integer.parseInt(octopus.group(3))),
                        lawn.centerY(Integer.parseInt(octopus.group(4)))
                                + cell * OCTOPUS_TO_LIFT_CELLS,
                        OCTOPUS, OCTOPUS_WIDTH_CELLS, STRIKE_LIFETIME, OCTOPUS_CLIP);
            }
        } catch (NumberFormatException ignored) {
            // a sentence shaped like a throw but not one; nothing to draw
        }
    }

    // World position, in the same space the projectile was drawn in. type may be null.
    public void spawn(float worldX, float worldY, Color color,
                      models.entities.projectiles.Element element,
                      models.entities.projectiles.ProjectileType type) {
        Burst burst = pool.obtain();
        burst.x = worldX;
        burst.y = worldY;
        // A splat STAYS WHERE IT LANDED. Leaving these at their 0f default made drawSplat interpolate
        // every splat from the hit toward world (0, 0) over its life -- a stream of pea fragments
        // sliding off to the bottom-left corner of the board after every single shot. It also tripped
        // the travelling-strike fade below, which holds full opacity for two thirds of the flight, so
        // the wrong thing was drawn brightly rather than subtly.
        burst.toX = worldX;
        burst.toY = worldY;
        burst.color.set(color);
        String byType = type == null ? null : SPLAT_BY_TYPE.get(type);
        burst.sprite = byType != null ? byType : SPLAT_BY_ELEMENT.getOrDefault(
                element == null ? models.entities.projectiles.Element.NEUTRAL : element,
                SPLAT_DEFAULT);
        burst.widthCells = SPLAT_WIDTH_BY_SPRITE.getOrDefault(burst.sprite, SPLAT_WIDTH_CELLS);
        // Cycled rather than random, so identical runs stay pixel-reproducible for the screenshot
        // harness -- the same reason the old burst used fixed angles.
        burst.clip = CLIPS[nextClip++ % CLIPS.length];
        bursts.add(burst);
    }

    // A named one-shot effect at a point -- the flash at a plant's mouth as it fires, rather than the
    // mark a shot leaves where it lands. Same machinery, different art and a different size.
    public void spawnMuzzle(float worldX, float worldY, String spriteName) {
        add(worldX, worldY, worldX, worldY, spriteName, MUZZLE_WIDTH_CELLS, LIFETIME, null);
    }

    // A strike: an effect that TRAVELS, from the plant that struck to whatever it struck.
    //
    // Caulipower and Electric Blueberry hit a zombie anywhere on the board with nothing in flight, so
    // there is no projectile to follow and no splat position to burst at -- only two points and the
    // knowledge that something went between them. Grave Buster uses the same call with both points the
    // same, because its dirt flies nowhere.
    public void spawnStrike(float fromX, float fromY, float toX, float toY, String spriteName) {
        add(fromX, fromY, toX, toY, spriteName, STRIKE_WIDTH_CELLS, STRIKE_LIFETIME, null);
    }

    // `clip` names one pose to hold; null cycles the CLIPS variants, which is what a repeated splat
    // wants and a single flying object does not -- an octopus in the air has exactly one right pose.
    private Burst add(float fromX, float fromY, float toX, float toY, String spriteName,
                      float widthCells, float lifetime, String clip) {
        if (spriteName == null) {
            return DISCARDED;
        }
        Burst burst = pool.obtain();
        burst.x = fromX;
        burst.y = fromY;
        burst.toX = toX;
        burst.toY = toY;
        burst.color.set(Color.WHITE);
        burst.sprite = spriteName;
        burst.widthCells = widthCells;
        burst.lifetime = lifetime;
        burst.clip = clip != null ? clip : CLIPS[nextClip++ % CLIPS.length];
        bursts.add(burst);
        return burst;
    }

    // Handed back when there is nothing to spawn, so a caller that wants to set one more field on the
    // burst it just asked for does not have to null-check. Never drawn: it is not in `bursts`.
    private static final Burst DISCARDED = new Burst();

    public void draw(Batch batch, float delta, float cellSize) {
        if (bursts.isEmpty()) {
            return;
        }
        float previous = batch.getPackedColor();

        for (int i = bursts.size() - 1; i >= 0; i--) {
            Burst burst = bursts.get(i);
            burst.age += delta;
            float life = burst.lifetime > 0f ? burst.lifetime : LIFETIME;
            if (burst.age >= life) {
                bursts.remove(i);
                pool.free(burst);
                continue;
            }
            float t = burst.age / life;
            // Fades out over its life. The clip itself is short, so without this the last frame would
            // pop rather than dissolve. A travelling strike holds its colour until the last third, or
            // it is already half gone by the time it reaches what it hit.
            boolean holds = burst.sustained || burst.toX != burst.x || burst.toY != burst.y;
            float fade = holds ? 1f - Math.max(0f, (t - 0.66f) / 0.34f) : 1f - t;
            batch.setColor(burst.color.r, burst.color.g, burst.color.b, fade);
            drawSplat(batch, burst, SpritePlacer.toSpriteSpace(burst.widthCells * cellSize), t);
        }
        batch.setPackedColor(previous);
    }

    private void drawSplat(Batch batch, Burst burst, float width, float t) {
        EntitySprite splat = sprites.get(burst.sprite);
        if (splat == null || !splat.isReady()) {
            return;
        }
        // "animation" behind the chosen variant, because firstAvailable's own fallback is `idle` and an
        // effect animation has no idle -- so a splat that ships fewer variants than CLIPS cycles through
        // was handed a clip name it does not carry, which resolves to nothing and draws nothing. The
        // snowball has two of the three, so one throw in three left no mark at all.
        String clip = ClipMap.firstAvailable(splat, burst.clip, "animation");
        com.badlogic.gdx.math.Rectangle bounds = splat.bounds(clip);
        if (bounds == null || bounds.width <= 0f) {
            return;
        }
        float scale = width / bounds.width;
        // Straight-line travel. A strike sets toX/toY to what it hit; everything else leaves them equal
        // to its own position, so this is the identity for a splat or a muzzle flash.
        float drawX = burst.x + (burst.toX - burst.x) * t;
        float drawY = burst.y + (burst.toY - burst.y) * t;

        transform.begin(batch, SpritePlacer.toSpriteSpace(drawX),
                SpritePlacer.toSpriteSpace(drawY), scale);
        // Same y-down correction as everywhere else: libPVZ reports bounds in the .PAM's Flash-style
        // coordinates, where the art hangs below the origin.
        // Sampled against the burst's OWN age, not against t scaled by the default lifetime. That is
        // what it used to be, and it meant every effect with a lifetime of its own played only the
        // first LIFETIME seconds of its clip stretched across the whole burst: a 0.63s snowball splat
        // showed 0.3s of animation in slow motion, and a beam held for two seconds would have shown a
        // seventh of itself. ClipMap.sample already wraps a looping clip and holds a one-shot on its
        // last frame, so age is the right thing to hand it either way.
        splat.draw(batch, clip, ClipMap.sample(splat, clip, burst.age),
                0f, bounds.y + bounds.height / 2f, true);
        transform.end(batch);
    }
}
