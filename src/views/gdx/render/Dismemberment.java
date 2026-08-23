package views.gdx.render;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.utils.Pool;
import views.gdx.sprite.EntitySprite;
import views.gdx.sprite.ZombieDamage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// The pieces that come off a zombie: a destroyed cone, bucket or brick, and the arm it loses when it is
// half chewed through.
//
// ## The art already knew how to do this
//
// Every shared zombie body ships a `particles` clip, and the clip's contents are exactly this task's
// list: a `_particles` group holding `particle_head` (skull, jaw and pupil), `particle_arm` (forearm and
// hand) and a copy of EVERY armor piece at every damage stage. Played bare it is a severed head and a
// severed arm tumbling away from where the zombie stood. So nothing here animates a trajectory or
// invents a tumbling sprite -- it plays the clip the artists authored and uses the visibility map to
// choose WHICH of the three flies, which is precisely what T8.5 asks for.
//
// The armor pieces inside `_particles` are authored switched OFF and the two limbs switched ON, so a
// piece of armor coming loose is "hide both limbs, show this hat" and a lost arm is "hide the head".
//
// ## Nothing throws a head
//
// The clip carries one, and there is deliberately no trigger for it. A head comes off when a zombie
// DIES, and death here is drawn by AshEffects -- a charred body collapsing into a heap. Throwing a
// bright green head off a zombie that is simultaneously turning to charcoal is two different deaths at
// once. The model's own hook for the other case, `StateComponent.isDecapitated()`, is read by nothing
// and **set by nothing** -- `setDecapitated` has no caller anywhere in the codebase -- so there is no
// state to drive it from either. Left for whoever wires that flag up.
public final class Dismemberment {

    private static final String CLIP = "particles";

    // The two limb groups inside `_particles`, switched off to leave only what actually came loose.
    private static final String PARTICLE_HEAD = "particle_head";
    private static final String PARTICLE_ARM = "particle_arm";

    // ## The clip is a POSE, not a movement, and the trajectory has to be ours
    //
    // `particles` runs 0.0333s -- a single frame. It draws a detached head and arm where they would be
    // an instant after coming off, and then it is over. Played as an animation it is one frame of a
    // flash and nothing more, which is exactly what the first run of this looked like. (The same trap
    // ZombieActions documents for the Imp's `fly` clip, and worth stating twice: a clip's NAME says
    // nothing about whether it moves.)
    //
    // So the pose is the art and the arc is the renderer's, the same split ProjectileRenderer already
    // makes for a lobbed shot -- purely visual, nothing reaching the model.
    //
    // Backwards and up. A pea travels left to right into a zombie walking right to left, so whatever it
    // knocks loose goes AWAY from the house -- a cone that flew toward the plants would read as the
    // zombie having thrown it.
    private static final float LAUNCH_X = 130f;
    private static final float LAUNCH_Y = 270f;
    private static final float GRAVITY = 700f;
    // A little over one turn across the flight. Enough to read as tumbling; more and a bucket becomes a
    // blur, which is worse than not spinning at all.
    private static final float SPIN_DEGREES_PER_SECOND = 420f;

    // Ends about where it would hit the ground: LAUNCH_Y * 2 / GRAVITY is 0.77s of flight.
    private static final float LIFETIME = 0.77f;

    // Fraction of the flight held at full opacity before it dissolves. A piece that simply disappeared
    // at ground level would read as a dropped frame.
    private static final float HOLD = 0.6f;

    private static final class Piece implements Pool.Poolable {
        float x;
        float footY;
        int row;
        float age;
        float lifetime;
        // +1 for a piece thrown to the right, -1 for one off a hypnotised zombie walking the other way.
        float direction;
        boolean faceRight;
        String sprite;
        // Rebuilt in place on every obtain, so a recycled Piece does not drag a fresh map along with it.
        final Map<String, Boolean> parts = new HashMap<>();

        @Override
        public void reset() {
            x = 0f;
            footY = 0f;
            row = 0;
            age = 0f;
            lifetime = 0f;
            direction = 1f;
            faceRight = false;
            sprite = null;
            parts.clear();
        }
    }

    private final Pool<Piece> pool = new Pool<>() {
        @Override
        protected Piece newObject() {
            return new Piece();
        }
    };

    private final views.gdx.sprite.SpriteRegistry sprites;
    private final List<Piece> pieces = new ArrayList<>();
    private final LocalTransform transform = new LocalTransform();

    public Dismemberment(views.gdx.sprite.SpriteRegistry sprites) {
        this.sprites = sprites;
    }

    // Throws one armor piece. `armorPart` is the part name ArmorVisibility built for that layer, so the
    // cone that flies off is the same cone -- at the same damage stage -- that was being worn.
    public void throwArmor(EntitySprite sprite, String spriteName, String armorPart,
                           float x, float footY, int row, boolean faceRight) {
        if (armorPart == null || sprite == null || !sprite.hasPart(armorPart)) {
            return;
        }
        Piece piece = spawn(sprite, spriteName, x, footY, row, faceRight);
        if (piece == null) {
            return;
        }
        piece.parts.put(PARTICLE_HEAD, false);
        piece.parts.put(PARTICLE_ARM, false);
        piece.parts.put(armorPart, true);
        log(spriteName, armorPart, row);
    }

    // Throws the arm the zombie has just lost. Only for animations that can actually show the loss --
    // otherwise an arm would fly off a zombie still visibly holding both.
    public void throwArm(EntitySprite sprite, String spriteName,
                         float x, float footY, int row, boolean faceRight) {
        if (!ZombieDamage.canLoseArm(sprite)) {
            return;
        }
        Piece piece = spawn(sprite, spriteName, x, footY, row, faceRight);
        if (piece == null) {
            return;
        }
        piece.parts.put(PARTICLE_HEAD, false);
        log(spriteName, PARTICLE_ARM, row);
    }

    // Names the part that actually flew. The effect lasts three quarters of a second on a board with a
    // dozen other things moving, so "something tumbled past" is not a check -- which piece the
    // visibility map switched on is.
    private static void log(String spriteName, String part, int row) {
        if (views.gdx.core.DebugFlags.BOARD_COUNTS) {
            com.badlogic.gdx.Gdx.app.log("Dismemberment",
                    spriteName + " sheds " + part + " in lane " + row);
        }
    }

    private Piece spawn(EntitySprite sprite, String spriteName, float x, float footY, int row,
                        boolean faceRight) {
        if (sprite == null || !sprite.isReady() || !sprite.hasClip(CLIP)) {
            return null;
        }
        Piece piece = pool.obtain();
        piece.sprite = spriteName;
        piece.x = x;
        piece.footY = footY;
        piece.row = row;
        piece.faceRight = faceRight;
        // Away from whatever hit it, which is the side the zombie is walking towards.
        piece.direction = faceRight ? -1f : 1f;
        // Not read from the clip: `particles` is one frame long. See the note above.
        piece.lifetime = LIFETIME;
        pieces.add(piece);
        return piece;
    }

    // Once per frame, never per lane: the lane pass visits drawRow five times a frame and ageing there
    // would run every throw at five times its own speed. The trap ZombieActions documents.
    public void advance(float delta) {
        for (int i = pieces.size() - 1; i >= 0; i--) {
            Piece piece = pieces.get(i);
            piece.age += delta;
            if (piece.age >= piece.lifetime) {
                pieces.remove(i);
                pool.free(piece);
            }
        }
    }

    // Drawn with the lane and after its zombies -- a piece flying off one of them belongs in front of
    // it, and behind whatever is standing in the row in front.
    public void drawRow(Batch batch, int row) {
        if (pieces.isEmpty()) {
            return;
        }
        float previous = batch.getPackedColor();
        for (Piece piece : pieces) {
            if (piece.row != row) {
                continue;
            }
            EntitySprite sprite = sprites.get(piece.sprite);
            if (sprite == null || !sprite.isReady()) {
                continue;
            }
            float t = piece.age;
            float fraction = t / piece.lifetime;
            float fade = fraction <= HOLD ? 1f : 1f - (fraction - HOLD) / (1f - HOLD);
            batch.setColor(1f, 1f, 1f, fade);

            // A plain ballistic arc from where it came off. Nothing about this reaches the model -- the
            // same split ProjectileRenderer makes for a lobbed shot.
            float dx = piece.direction * LAUNCH_X * t;
            float dy = LAUNCH_Y * t - 0.5f * GRAVITY * t * t;

            // Rotated about the point it is drawn from rather than about its own middle. Measuring the
            // middle would mean visibleBounds against the hidden-part set on every frame, and at this
            // size and speed the difference between a spin and a tight orbit is not visible.
            transform.begin(batch,
                    SpritePlacer.toSpriteSpace(piece.x + dx),
                    SpritePlacer.toSpriteSpace(piece.footY + dy),
                    1f, piece.direction * SPIN_DEGREES_PER_SECOND * t);
            // stateTime 0: the clip is a single frame, so there is nothing to sample into.
            sprite.draw(batch, CLIP, 0f, 0f, 0f, piece.faceRight, piece.parts);
            transform.end(batch);
        }
        batch.setPackedColor(previous);
    }
}
