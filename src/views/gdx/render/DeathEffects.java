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

// What a zombie leaves behind, which is one of two different things.
//
// **A zombie that is killed falls over: it plays its own `die` clip.** **A zombie caught in a blast is
// reduced to ash.** That is the game's own language, and getting it wrong -- ashing every death,
// whatever killed it -- makes every pea look like a bomb and wastes the `die` animation the dump ships
// for all 27 zombies.
//
// ## Why this exists at all
//
// `CombatSystem.processDeaths` removes a zombie from its Row on the very tick it dies, and that runs
// inside `advanceOneTick` -- so by the time any frame is drawn the zombie is already gone, and its
// `die` clip is never played by ZombieRenderer because there is no longer a zombie to play it on. What
// the player used to watch was a zombie blinking out of existence mid-stride. This is the corpse: a
// view-side effect, detached from the entity, which is the only way either animation can be seen.
//
// (Holding corpses in the model for a couple of seconds was the other candidate fix and is the wrong
// one: it puts a cosmetic lifetime into the tick loop, and every rule that counts zombies -- the 75%
// next-wave threshold, the lose check, the quest tallies -- would have to learn to ignore them.)
//
// ## Telling the two apart
//
// The model does not record HOW a zombie died: `HealthComponent.applyDamage` takes an `Element` and
// keeps only the attacker. Rather than widen the model for a purely cosmetic distinction, the view
// answers it from something it already tracks -- `ExplosionEffects` knows where every live blast is.
// See ExplosionEffects.killedByBlast.
//
// **The question is asked a frame after the death, not on it.** That is not a detail; it is the whole
// reason ash was never seen. A tick's narration reaches the view down TWO queues, and they arrive in
// the order the queues are drained rather than the order the events happened:
//
//   * `CombatSystem.processTick` RETURNS its death lines, and `GameEngine.advanceOneTick` renders that
//     list immediately.
//   * `AreaExplosiveAbility.detonate` calls `gameSession.reportEvent`, which lands in the session's
//     domain-event queue -- drained AFTERWARDS, at the end of the same tick.
//
// So for every zombie a blast kills outright, "the zombie is dead" arrives BEFORE "the bomb went off",
// and asking on the spot always answered no. Every explosive kill in the game fell over as a corpse
// instead of turning to ash, which is exactly the report this came from. (A blast that only WOUNDS a
// zombie looked fine, because the finishing blow lands on a later tick, by which point the detonation
// has long since been drained -- so the bug hid behind the cases that happened to work.)
//
// Holding the decision until `advance` runs -- once per frame, after the drain that carried both
// sentences -- makes it independent of which queue won the race. Reordering the model's two queues
// would be the other fix and is the wrong one: that order is the terminal build's printed output.
//
// ## The ash art is not what its name suggests
//
// `ZOMBIE_ASH` is not a puff of smoke. It is the ZOMBIE, charred black, over 3.5s: it stands there
// burnt, collapses into a smouldering heap, and the heap fades itself out. `ZOMBIE_GARGANTUAR_ASH` is a
// charred Gargantuar with its Imp still on its back. So both of this class's outcomes are drawn the
// same way -- standing on the lane's foot line at the zombie's own scale, in the lane pass -- and
// neither is fitted to a cell or lifted off the ground.
public final class DeathEffects {

    // CombatSystem.reportZombieDeath: "Zombie of type ZombieDefault is dead at (3, 2)". No trailing
    // period, and the x is `(int)` of a continuous double -- which can be NEGATIVE for a zombie that
    // died a step past the house, so the sign is matched rather than assumed.
    private static final Pattern DEATH =
            Pattern.compile("^Zombie of type (.+?) is dead at \\((-?\\d+), (\\d+)\\)$");

    private static final String ASH_DEFAULT = "ZOMBIE_ASH";
    private static final String ASH_GARGANTUAR = "ZOMBIE_GARGANTUAR_ASH";
    private static final String ASH_IMP = "ZOMBIE_IMP_ASH";

    // Matched on the alias, which is all the event carries -- the Zombie object is long gone by the
    // time this runs, so there is nothing left to ask for a category.
    private static final String GARGANTUAR = "gargantuar";
    private static final String IMP = "imp";

    private static final String DIE_CLIP = "die";
    private static final String ASH_CLIP = "animation";

    // Fallback only, for art whose duration cannot be read. The real length comes from the clip, the
    // same rule ExplosionEffects follows.
    private static final float FALLBACK_LIFETIME = 2.0f;

    // A corpse holds its final pose and then fades. The ash animation dissolves ITSELF, so only the
    // `die` clip needs this -- a zombie that lay on the lawn and then vanished in one frame would read
    // as a dropped frame rather than as a body being cleared.
    private static final float CORPSE_FADE = 0.25f;

    private static final class Remains implements Pool.Poolable {
        float x;
        int row;
        float age;
        float lifetime;
        String sprite;
        String clip;
        // Ash fades itself; a corpse does not.
        boolean fades;

        @Override
        public void reset() {
            x = 0f;
            row = 0;
            age = 0f;
            lifetime = 0f;
            sprite = null;
            clip = null;
            fades = false;
        }
    }

    // A zombie dying is one of the most frequent events in the game -- several hundred over a level --
    // so these are recycled rather than allocated.
    private final Pool<Remains> pool = new Pool<>() {
        @Override
        protected Remains newObject() {
            return new Remains();
        }
    };

    private final SpriteRegistry sprites;
    private final LawnGeometry lawn;
    private final List<Remains> remains = new ArrayList<>();

    // Consulted to find out whether a blast is what killed it. Set by GameRenderer.
    private ExplosionEffects explosions;

    // Where the head goes. Only the corpse path uses it -- a zombie reduced to ash has no head left to
    // come off, and throwing one out of a cloud of cinders would be two deaths at once.
    private Dismemberment pieces;

    void setPieces(Dismemberment pieces) {
        this.pieces = pieces;
    }

    // Where the most recent zombie died, kept because this class is the only thing in the view that
    // knows -- the model deletes the zombie on the tick it dies, so by the time anything else looks
    // there is nothing left to ask. ScorePopups uses it to float a Meow Point award off the kill that
    // earned it; a scoring rule fires in the same drain as the death that triggered it, so this is
    // still the right zombie when it is read.
    //
    // Defaults to the middle of the board rather than (0, 0), so the first read on a board where
    // nothing has died yet puts a label somewhere sensible instead of in the top-left corner.
    private float lastDeathX;
    private int lastDeathRow = utils.Constants.BOARD_ROWS / 2;

    public float lastDeathX() {
        return lastDeathX == 0f ? lawn.centerX(utils.Constants.BOARD_COLS / 2) : lastDeathX;
    }

    public int lastDeathRow() {
        return lastDeathRow;
    }

    // Zombotany's plant heads, shared with ZombieRenderer so a corpse is measured and scaled exactly as
    // the living zombie was. Set by GameRenderer.
    private ZombotanyHead botany;

    void setBotany(ZombotanyHead botany) {
        this.botany = botany;
    }

    public DeathEffects(SpriteRegistry sprites, LawnGeometry lawn) {
        this.sprites = sprites;
        this.lawn = lawn;
    }

    void setExplosions(ExplosionEffects explosions) {
        this.explosions = explosions;
    }

    // Offered every event the model drains, alongside the explosions, the weather, the zombie actions,
    // the camera shake and the audio cues. Anything that is not a death is ignored.
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
        // Clamped, because a zombie can die at a negative x a step past the house.
        int col = Math.max(0, Math.min(utils.Constants.BOARD_COLS - 1,
                Integer.parseInt(matcher.group(2))));

        // Queued rather than decided here -- see the note at the top of this class about the two
        // queues. Settled at the start of the next advance(), by which point every detonation from the
        // same tick has been drained and killedByBlast can actually answer.
        pending.add(new PendingDeath(alias, col, row));

        // These two are NOT deferred. A scoring rule fires inside the same drain as the death that
        // triggered it and reads them straight away, so they have to be true the moment the sentence
        // arrives, not a frame later.
        lastDeathX = lawn.centerX(col);
        lastDeathRow = row;
    }

    // A death that has arrived but has not yet been told apart from an explosive one.
    private record PendingDeath(String alias, int col, int row) { }

    private final List<PendingDeath> pending = new ArrayList<>();

    // Turns each queued death into a corpse or a heap of ash. Called once per frame from advance(),
    // which is the first point at which the whole of the tick's narration has been delivered.
    private void settlePending() {
        if (pending.isEmpty()) {
            return;
        }
        for (PendingDeath death : pending) {
            boolean blasted = explosions != null && explosions.killedByBlast(death.col(), death.row());
            Remains dead = blasted ? asAsh(death.alias()) : asCorpse(death.alias());
            if (dead == null) {
                continue;
            }
            // Placed on the tile the event names, exactly as ExplosionEffects places a blast from the
            // same shape of sentence. The column is floored off a continuous x, so this is up to half a
            // cell out; that is the price of the view never being handed the Zombie, and it is the
            // approximation every other event-driven effect here already accepts.
            dead.x = lawn.centerX(death.col());
            dead.row = death.row();
            remains.add(dead);

            // The head comes off with the body, not when it finishes falling. See Dismemberment.
            // throwHead for why this is what stops a kill feeling delayed. Ash has no head to throw.
            if (!blasted && pieces != null) {
                popHead(death.alias(), dead.x,
                        lawn.worldY(death.row()) + lawn.cellHeight() * SpritePlacer.FOOT_INSET,
                        death.row());
            }

            if (views.gdx.core.DebugFlags.BOARD_COUNTS) {
                com.badlogic.gdx.Gdx.app.log("DeathEffects",
                        death.alias() + (blasted ? " is blasted to " : " dies as ")
                                + dead.sprite + " [" + dead.clip + "] at (" + death.col() + ", "
                                + death.row() + ") for " + dead.lifetime + "s");
            }
        }
        pending.clear();
    }

    // Which head comes off, which depends entirely on which head the player was just looking at.
    //
    // For every zombie in the game that is the shared body's own severed skull, out of the `particles`
    // pose. For a Zombotany zombie it is the PLANT: ZombotanyHead has been drawing one on its neck all
    // level, and popping a green skull off it instead would contradict the last frame the player saw.
    private void popHead(String alias, float x, float footY, int row) {
        String plant = ZombotanyHead.plantFor(alias);
        EntitySprite body = sprites.get(alias);
        if (plant == null || botany == null) {
            pieces.throwHead(body, alias, x, footY, row, false);
            return;
        }
        pieces.throwWhole(plant, ZombotanyHead.plantClip(),
                botany.headScale(alias, body, DIE_CLIP), x, footY, row, false);
    }

    // The ordinary death: the zombie's own body, playing its own `die` clip where it fell.
    //
    // Falls back to ash if the animation has no `die` -- better a wrong-looking death than a zombie that
    // still vanishes into nothing, which is the bug this whole class exists to fix.
    private Remains asCorpse(String alias) {
        EntitySprite sprite = sprites.get(alias);
        if (sprite == null || !sprite.isReady() || !sprite.hasClip(DIE_CLIP)) {
            return asAsh(alias);
        }
        Remains dead = pool.obtain();
        dead.sprite = alias;
        dead.clip = DIE_CLIP;
        dead.fades = true;
        dead.lifetime = durationOf(sprite, DIE_CLIP);
        return dead;
    }

    // Caught in a blast. A Gargantuar's remains and an Imp's are both in the dump, and the difference
    // is worth having: one is four times the other's height, and a single sprite would have a
    // Gargantuar leaving a browncoat's heap behind.
    private Remains asAsh(String alias) {
        String lower = alias == null ? "" : alias.toLowerCase(Locale.ROOT);
        String sprite = ASH_DEFAULT;
        if (lower.contains(GARGANTUAR)) {
            sprite = ASH_GARGANTUAR;
        } else if (lower.contains(IMP)) {
            sprite = ASH_IMP;
        }
        Remains dead = pool.obtain();
        dead.sprite = sprite;
        dead.clip = ASH_CLIP;
        // The ash animation ends by dissolving the heap, so nothing here should fade it as well.
        dead.fades = false;
        dead.lifetime = durationOf(sprites.get(sprite), ASH_CLIP);
        return dead;
    }

    private float durationOf(EntitySprite sprite, String clip) {
        if (sprite == null || !sprite.isReady()) {
            return FALLBACK_LIFETIME;
        }
        float duration = sprite.clipDuration(ClipMap.firstAvailable(sprite, clip));
        return duration > 0f ? duration : FALLBACK_LIFETIME;
    }

    // Ages everything and drops what is finished. Called ONCE per frame from GameRenderer, never from
    // drawRow -- the lane pass visits this five times a frame, and ageing there would run every death at
    // five times its own speed. The same trap ZombieActions documents.
    public void advance(float delta) {
        // First, because a death queued during the last frame's drain becomes a body here and should
        // be drawn by the row pass that follows in this very frame.
        settlePending();
        for (int i = remains.size() - 1; i >= 0; i--) {
            Remains dead = remains.get(i);
            dead.age += delta;
            if (dead.age >= dead.lifetime) {
                remains.remove(i);
                pool.free(dead);
            }
        }
    }

    // Drawn with the lane and after its zombies: a body is the same size and on the same ground as the
    // zombie it stands in for, so it has to be occluded by the row in front the same way -- and behind
    // the living, because the horde walking over its own dead is what the row should read as.
    public void drawRow(Batch batch, int row) {
        if (remains.isEmpty()) {
            return;
        }
        float previous = batch.getPackedColor();
        float footY = lawn.worldY(row) + lawn.cellHeight() * SpritePlacer.FOOT_INSET;
        for (Remains dead : remains) {
            if (dead.row != row) {
                continue;
            }
            EntitySprite sprite = sprites.get(dead.sprite);
            if (sprite == null || !sprite.isReady()) {
                continue;
            }
            String clip = ClipMap.firstAvailable(sprite, dead.clip);
            batch.setColor(1f, 1f, 1f, alphaOf(dead));
            float stateTime = ClipMap.sample(sprite, clip, dead.age);
            // A Zombotany corpse topples wearing the plant it was wearing a frame ago. dead.sprite is
            // the alias on the corpse path and an ash animation's name on the other, and plantFor
            // answers null for both an ordinary zombie and for ash -- so this needs no test of its own.
            java.util.Map<String, Boolean> parts =
                    ZombotanyHead.hideSkull(dead.sprite, sprite, null);
            // No width fitting: both the bodies and the ash are authored at the same resolution as the
            // zombies, so the correct amount of interference is none. Facing left, which is the way
            // every zombie on this board was walking.
            SpritePlacer.drawStanding(batch, sprite, clip, stateTime, dead.x, footY, false, parts);
            if (botany != null) {
                botany.draw(batch, dead.sprite, sprite, clip, stateTime, dead.x, footY, false);
            }
        }
        batch.setPackedColor(previous);
    }

    private static float alphaOf(Remains dead) {
        if (!dead.fades) {
            return 1f;
        }
        float remaining = 1f - dead.age / dead.lifetime;
        return remaining >= CORPSE_FADE ? 1f : remaining / CORPSE_FADE;
    }

    // No clear(): a restart builds a whole new GameScreen, and with it a new GameRenderer and a new
    // instance of this, so there is nothing to carry over and nothing for such a method to do.
}
