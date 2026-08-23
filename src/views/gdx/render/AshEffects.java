package views.gdx.render;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.utils.Pool;
import views.gdx.map.LawnGeometry;
import views.gdx.sprite.ClipMap;
import views.gdx.sprite.EntitySprite;
import views.gdx.sprite.SpriteRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// What a zombie leaves behind when it dies.
//
// This is the one effect in Phase 8 that is not polish. `CombatSystem.processDeaths` removes a zombie
// from its Row on the very tick it dies, and that runs inside `advanceOneTick` -- so by the time any
// frame is drawn, the zombie is already gone. Its `die` clip, which every zombie animation in the dump
// ships, is therefore NEVER SEEN: what the player actually watches is a zombie blinking out of
// existence mid-stride. This is what marks the death instead, and it can only do so because it is not
// attached to the entity that died.
//
// (Holding corpses in the model for a second would be the other fix, and is the wrong one: it puts a
// purely cosmetic lifetime into the tick loop, and every rule that counts zombies -- the 75% next-wave
// threshold, the lose check, the quest tallies -- would have to learn to ignore them.)
//
// ## The art is not what its name suggests
//
// `ZOMBIE_ASH` is not a puff of smoke. It is the ZOMBIE, charred black, over a 3.5s sequence: it stands
// there burnt, collapses into a smouldering heap, and the heap fades out. `ZOMBIE_GARGANTUAR_ASH` is a
// charred Gargantuar, Imp still on its back. Three consequences, all of which the first pass got wrong
// by treating it as a particle cloud:
//
//  * it is drawn STANDING on the lane's foot line at the zombie's own scale, not centred on a lifted
//    point and squeezed to a cell -- otherwise a full-height charred zombie floats half a lane up;
//  * it is drawn IN THE LANE PASS, after the zombies, because it is a body-sized thing that has to be
//    occluded by the row in front exactly as the zombie it replaces was;
//  * it needs no fade of its own, and must not be cut short. The animation ends by dissolving the heap;
//    cutting it at a second, as the first pass did, would end on a black zombie still standing.
public final class AshEffects {

    // CombatSystem.reportZombieDeath: "Zombie of type ZombieDefault is dead at (3, 2)". No trailing
    // period, and the x is `(int)` of a continuous double -- which can be NEGATIVE for a zombie that
    // died a step past the house, so the sign is matched rather than assumed.
    private static final Pattern DEATH =
            Pattern.compile("^Zombie of type (.+?) is dead at \\((-?\\d+), (\\d+)\\)$");

    private static final String ASH_DEFAULT = "ZOMBIE_ASH";
    private static final String ASH_GARGANTUAR = "ZOMBIE_GARGANTUAR_ASH";
    private static final String ASH_IMP = "ZOMBIE_IMP_ASH";

    // Matched on the alias, which is all the event carries -- the Zombie object is long gone by the time
    // this runs, so there is nothing left to ask for a category.
    private static final String GARGANTUAR = "gargantuar";
    private static final String IMP = "imp";

    // Fallback only, for art whose duration cannot be read. The real length comes from the clip, the
    // same rule ExplosionEffects follows: an effect that outlives its animation holds its last frame,
    // which reads as a freeze, and one that is cut short vanishes mid-pose.
    private static final float FALLBACK_LIFETIME = 3.5f;

    private static final class Puff implements Pool.Poolable {
        float x;
        int row;
        float age;
        float lifetime;
        String sprite;

        @Override
        public void reset() {
            x = 0f;
            row = 0;
            age = 0f;
            lifetime = 0f;
            sprite = null;
        }
    }

    // A zombie dying is one of the most frequent events in the game -- several hundred over a level --
    // so these are recycled rather than allocated. Same reason the blueprint asked for a pool.
    private final Pool<Puff> pool = new Pool<>() {
        @Override
        protected Puff newObject() {
            return new Puff();
        }
    };

    private final SpriteRegistry sprites;
    private final LawnGeometry lawn;
    private final List<Puff> puffs = new ArrayList<>();

    public AshEffects(SpriteRegistry sprites, LawnGeometry lawn) {
        this.sprites = sprites;
        this.lawn = lawn;
    }

    // Offered every event the model drains, alongside the explosions, the weather, the zombie actions
    // and the camera shake. Anything that is not a death is ignored.
    public void onEvent(String message) {
        if (message == null) {
            return;
        }
        Matcher matcher = DEATH.matcher(message.trim());
        if (!matcher.matches()) {
            return;
        }
        String alias = matcher.group(1).trim();
        int row = Integer.parseInt(matcher.group(3));
        if (row < 0 || row >= utils.Constants.BOARD_ROWS) {
            return;
        }

        Puff puff = pool.obtain();
        puff.sprite = ashFor(alias);
        puff.row = row;
        // Placed on the tile the event names, exactly as ExplosionEffects places a blast from the same
        // shape of sentence. The column is floored off a continuous x, so this is up to half a cell out;
        // that is the price of the view never being handed the Zombie, and it is the approximation every
        // other event-driven effect here already accepts. Clamped, because a zombie can die at a
        // negative x a step past the house.
        int col = Math.max(0, Math.min(utils.Constants.BOARD_COLS - 1,
                Integer.parseInt(matcher.group(2))));
        puff.x = lawn.centerX(col);
        puff.lifetime = lifetimeOf(puff.sprite);
        puffs.add(puff);

        if (views.gdx.core.DebugFlags.BOARD_COUNTS) {
            com.badlogic.gdx.Gdx.app.log("AshEffects", alias + " -> " + puff.sprite
                    + " at (" + col + ", " + row + ") for " + puff.lifetime + "s");
        }
    }

    // A Gargantuar's remains and an Imp's are both in the dump, and the difference is worth having: one
    // is four times the other's height, and a single sprite would have a Gargantuar leaving a browncoat's
    // heap behind.
    private static String ashFor(String alias) {
        String lower = alias == null ? "" : alias.toLowerCase(Locale.ROOT);
        if (lower.contains(GARGANTUAR)) {
            return ASH_GARGANTUAR;
        }
        if (lower.contains(IMP)) {
            return ASH_IMP;
        }
        return ASH_DEFAULT;
    }

    private float lifetimeOf(String spriteName) {
        EntitySprite sprite = sprites.get(spriteName);
        if (sprite == null || !sprite.isReady()) {
            return FALLBACK_LIFETIME;
        }
        float duration = sprite.clipDuration(ClipMap.firstAvailable(sprite, "animation"));
        return duration > 0f ? duration : FALLBACK_LIFETIME;
    }

    // Ages every heap and drops the finished ones. Called ONCE per frame from GameRenderer, never from
    // drawRow -- the lane pass visits this five times a frame, and ageing there would run every death
    // at five times its own speed. The same trap ZombieActions documents.
    public void advance(float delta) {
        for (int i = puffs.size() - 1; i >= 0; i--) {
            Puff puff = puffs.get(i);
            puff.age += delta;
            if (puff.age >= puff.lifetime) {
                puffs.remove(i);
                pool.free(puff);
            }
        }
    }

    // Drawn with the lane, after its zombies: the charred body is the same size and on the same ground
    // as the zombie it stands in for, so it has to be occluded by the row in front the same way.
    public void drawRow(Batch batch, int row) {
        if (puffs.isEmpty()) {
            return;
        }
        float footY = lawn.worldY(row) + lawn.cellHeight() * SpritePlacer.FOOT_INSET;
        for (Puff puff : puffs) {
            if (puff.row != row) {
                continue;
            }
            EntitySprite sprite = sprites.get(puff.sprite);
            if (sprite == null || !sprite.isReady()) {
                continue;
            }
            String clip = ClipMap.firstAvailable(sprite, "animation");
            // No fade applied here, and no width fitting: the animation dissolves itself and is authored
            // at the same resolution as the zombies, so the correct amount of interference is none.
            // Facing left, which is the way every zombie on this board was walking.
            SpritePlacer.drawStanding(batch, sprite, clip,
                    ClipMap.sample(sprite, clip, puff.age), puff.x, footY, false, null);
        }
    }

    // No clear(): a restart builds a whole new GameScreen, and with it a new GameRenderer and a new
    // instance of this, so there is nothing to carry over and nothing for such a method to do.
}
