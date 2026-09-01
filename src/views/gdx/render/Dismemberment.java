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
    //
    // Armor and the severed arm only. A HEAD is not knocked off by the shot that lands, it comes off
    // because the zombie died, so it is thrown at random instead -- see randomiseHead.
    private static final float LAUNCH_X = 130f;
    private static final float LAUNCH_Y = 270f;
    private static final float GRAVITY = 700f;

    // ## Nothing here rotates, and that is the whole point
    //
    // A piece used to be spun as it flew, and it did not read as a tumble -- it read as the piece
    // ORBITING, on a wide circular path with no parabola visible in it at all.
    //
    // That was not a matter of too many degrees per second. A .PAM is drawn from the body's origin, down
    // at the zombie's feet, and every part is posed at an offset from it -- a head sits a couple of
    // hundred pixels UP. Rotating the transform about that origin therefore sweeps the head around a
    // circle of that radius, and the drawn position is the arc PLUS that circle. With a radius bigger
    // than the arc's own height, the circle is all you see. (The old comment here claimed the difference
    // between spinning about the origin and about the piece's own middle "is not visible at this size";
    // it is the only thing that was visible.)
    //
    // Rotating about the measured centre instead would fix the path and cost a visibleBounds call
    // against the hidden-part set every frame. Not worth it: a clean parabola is what was asked for, and
    // it is also what the pieces actually want to do.

    // ## A head goes wherever it goes
    //
    // The spec asks for the head specifically to be thrown "in a random direction -- once backwards,
    // once forwards", and that is not decoration: every other piece here comes off because something hit
    // it, so a fixed away-from-the-shot arc is the correct reading for a cone or an arm. A head that
    // always flew the same way, at the same speed, off every zombie in a wave of ten made ten identical
    // parabolas -- which reads as one canned effect rather than as ten zombies falling apart.
    //
    // Both the sign and the magnitude are drawn, and so is the lift. The sign is what the spec names;
    // the rest is what stops two heads thrown the same way looking like copies of each other. Horizontal
    // speed is kept off zero at the bottom of its band so a head never simply rises and drops back onto
    // its own body.
    private static final float HEAD_SPEED_MIN = 70f;
    private static final float HEAD_SPEED_MAX = 190f;
    private static final float HEAD_LIFT_MIN = 220f;
    private static final float HEAD_LIFT_MAX = 340f;

    // How long a piece lies where it landed before it is cleared. The spec wants the pieces to fall ON
    // the ground and allows them to vanish outright afterwards, so this is short and there is no bounce.
    // Zero would be wrong: the flight ends at ground level, so a piece removed on that frame is gone on
    // the first frame it has actually landed, and nothing is ever seen lying on the lawn.
    private static final float GROUND_REST = 0.45f;

    // Seconds of fade at the very end. A piece that blinked out would read as a dropped frame.
    private static final float FADE = 0.3f;

    private static final class Piece implements Pool.Poolable {
        float x;
        float footY;
        int row;
        float age;
        float lifetime;
        // Launch velocity in world pixels per second. Held per piece rather than as constants because a
        // head's is drawn at random and an armor piece's is not.
        float velocityX;
        float velocityY;
        // When it hits the ground: 2 * velocityY / GRAVITY. Held rather than recomputed so the draw pass
        // can clamp the arc to it and leave the piece lying still.
        float flight;
        // +1 for a piece thrown to the right, -1 for one off a hypnotised zombie walking the other way.
        float direction;
        boolean faceRight;
        String sprite;
        // Which clip of `sprite` to draw, and at what size. Defaults to the shared `particles` pose at
        // 1x, which is what every piece that comes off a zombie body is; a Zombotany plant head is a
        // whole other animation at head size, which is what these two fields are for.
        String clip;
        float scale;
        // Rebuilt in place on every obtain, so a recycled Piece does not drag a fresh map along with it.
        final Map<String, Boolean> parts = new HashMap<>();

        @Override
        public void reset() {
            x = 0f;
            footY = 0f;
            row = 0;
            age = 0f;
            lifetime = 0f;
            velocityX = 0f;
            velocityY = 0f;
            flight = 0f;
            direction = 1f;
            faceRight = false;
            sprite = null;
            clip = CLIP;
            scale = 1f;
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

    // Throws the head, on the instant a zombie is killed.
    //
    // This is what makes a death REGISTER. The `die` clips are slow topples with a long static lead-in
    // -- a Gargantuar's is 2.6s and spends its first 0.8s essentially standing there -- and because the
    // model deletes the zombie the moment it dies, the corpse appears already standing and nothing
    // visibly changes for most of a second. That reads as the game lagging behind the shot that killed
    // it. A head coming off on the exact frame of death gives the kill its moment, and the slump behind
    // it then reads as follow-through rather than delay.
    //
    // Verified against the art before wiring: the `die` clips do NOT remove the head themselves, so
    // this adds a beat rather than doubling one.
    public void throwHead(EntitySprite sprite, String spriteName,
                          float x, float footY, int row, boolean faceRight) {
        Piece piece = spawn(sprite, spriteName, x, footY, row, faceRight);
        if (piece == null) {
            return;
        }
        piece.parts.put(PARTICLE_ARM, false);
        randomiseHead(piece);
        log(spriteName, PARTICLE_HEAD, row);
        if (audio != null) {
            audio.play(views.gdx.core.AudioManager.forEntity(
                    views.gdx.core.AudioManager.SFX_HEAD_POP, spriteName),
                    views.gdx.core.AudioManager.SFX_HEAD_POP);
        }
    }

    // Optional, and null in any harness with no game around it.
    private views.gdx.core.AudioManager audio;

    public void setAudio(views.gdx.core.AudioManager audio) {
        this.audio = audio;
    }

    // Throws a WHOLE sprite rather than a part of the zombie's own body, on the same arc.
    //
    // Zombotany is what needs it: a plant-headed zombie whose death popped the shared body's green
    // skull would be shedding a head it visibly did not have. Its head is the plant's own animation, so
    // what comes off has to be that -- drawn at the size ZombotanyHead was wearing it at, or it doubles
    // in size on the frame it comes loose.
    //
    // Everything else about the throw is unchanged: same arc, same fade, same lane.
    public void throwWhole(String spriteName, String clip, float scale,
                           float x, float footY, int row, boolean faceRight) {
        EntitySprite sprite = spriteName == null ? null : sprites.get(spriteName);
        if (sprite == null || !sprite.isReady() || !sprite.hasClip(clip)) {
            return;
        }
        Piece piece = pool.obtain();
        piece.sprite = spriteName;
        piece.clip = clip;
        piece.scale = scale;
        piece.x = x;
        piece.footY = footY;
        piece.row = row;
        piece.faceRight = faceRight;
        piece.direction = faceRight ? -1f : 1f;
        // A Zombotany head is still a head, so it is thrown like one.
        randomiseHead(piece);
        pieces.add(piece);
        log(spriteName, clip, row);
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
        piece.clip = CLIP;
        piece.scale = 1f;
        piece.x = x;
        piece.footY = footY;
        piece.row = row;
        piece.faceRight = faceRight;
        // Away from whatever hit it, which is the side the zombie is walking towards.
        piece.direction = faceRight ? -1f : 1f;
        launch(piece, piece.direction * LAUNCH_X, LAUNCH_Y);
        pieces.add(piece);
        return piece;
    }

    // Gives a piece its arc, and sizes its life to it.
    //
    // The lifetime is NOT read from the clip: `particles` is one frame long (see the note above), so the
    // only thing that can say how long the piece should exist is how long it takes to come down.
    private static void launch(Piece piece, float velocityX, float velocityY) {
        piece.velocityX = velocityX;
        piece.velocityY = velocityY;
        piece.flight = 2f * velocityY / GRAVITY;
        piece.lifetime = piece.flight + GROUND_REST;
    }

    // Throws a head somewhere other than where the last one went. See the note on HEAD_SPEED_MIN.
    private static void randomiseHead(Piece piece) {
        float sign = com.badlogic.gdx.math.MathUtils.randomBoolean() ? 1f : -1f;
        launch(piece,
                sign * com.badlogic.gdx.math.MathUtils.random(HEAD_SPEED_MIN, HEAD_SPEED_MAX),
                com.badlogic.gdx.math.MathUtils.random(HEAD_LIFT_MIN, HEAD_LIFT_MAX));
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
            // Clamped at the landing, so the last stretch of the piece's life is spent lying still on
            // the lawn rather than continuing down through it.
            float t = Math.min(piece.age, piece.flight);
            float remaining = piece.lifetime - piece.age;
            batch.setColor(1f, 1f, 1f, remaining >= FADE ? 1f : Math.max(remaining, 0f) / FADE);

            // A plain ballistic arc from where it came off. Nothing about this reaches the model -- the
            // same split ProjectileRenderer makes for a lobbed shot.
            float dx = piece.velocityX * t;
            float dy = piece.velocityY * t - 0.5f * GRAVITY * t * t;

            // Translate and scale only. See the note on GRAVITY for why there is no rotation.
            transform.begin(batch,
                    SpritePlacer.toSpriteSpace(piece.x + dx),
                    SpritePlacer.toSpriteSpace(piece.footY + dy),
                    piece.scale);
            // stateTime 0: `particles` is a single frame, so there is nothing to sample into -- and a
            // thrown whole sprite is held on its opening pose for the same reason a corpse is, because
            // what is being watched is the arc rather than the animation.
            sprite.draw(batch, piece.clip, 0f, 0f, 0f, piece.faceRight,
                    piece.parts.isEmpty() ? null : piece.parts);
            transform.end(batch);
        }
        batch.setPackedColor(previous);
    }
}
