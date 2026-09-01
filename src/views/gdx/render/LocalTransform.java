package views.gdx.render;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Matrix4;

// Pushes a translate-and-scale onto the batch's transform, and puts back what was there.
//
// Every effect that draws a .PAM at its own size has to do this, because a PAM has no scale of its own
// and its parts would otherwise each have to be scaled about the right point. The obvious spelling --
//
//     Matrix4 previous = batch.getTransformMatrix().cpy();
//     batch.setTransformMatrix(new Matrix4(previous).translate(...).scale(...));
//
// -- allocates TWO 64-byte matrices every time, and these are per-effect per-FRAME calls: a Jalapeno's
// row burn alone is nine copies, so it was producing eighteen matrices a frame on its own, and a busy
// board with a dozen splats in the air produced twenty-four more. That is the garbage a pool is
// supposed to stop, and it was the largest source of it in the renderer.
//
// Each renderer owns its own instance rather than sharing a static pair, so two of them pushing in the
// same frame cannot tread on each other's saved matrix. Calls must not NEST on one instance -- they
// never do, because every use is push, draw, pop with nothing in between.
final class LocalTransform {

    private final Matrix4 saved = new Matrix4();
    private final Matrix4 scratch = new Matrix4();

    // Coordinates are in SPRITE space (see SpritePlacer.toSpriteSpace), because the whole entity pass is
    // already drawn through the scaled transform this multiplies into.
    // Translate and scale, with no rotation on offer.
    //
    // There was a rotating overload, for the dismemberment pieces to tumble with. It is gone because
    // rotating HERE can only ever turn a .PAM about its own draw origin -- which for a zombie body is
    // down at its feet, a couple of hundred pixels below the part being thrown -- so it swung the piece
    // around a wide circle instead of spinning it in place, and buried the ballistic arc underneath.
    // Anything that wants a real tumble has to measure the piece's centre first; see Dismemberment.
    void begin(Batch batch, float spriteX, float spriteY, float scale) {
        begin(batch, spriteX, spriteY, scale, false);
    }

    // Mirroring, unlike rotation, IS safe to do here -- and is in fact the only place it can be done.
    //
    // The EntitySprite `faceRight` flag does not flip anything (see SpritePlacer.drawStandingScaled for
    // the full account: libPVZ reads it as LOOP), so a negative x scale on the transform is what a
    // mirror actually is. The trap rotation falls into does not apply: flipping about the draw point is
    // wrong for a sprite hung off its feet and exactly right for one drawn centred on that point, which
    // is what every caller of this class does.
    void begin(Batch batch, float spriteX, float spriteY, float scale, boolean mirror) {
        // set(), not cpy(): getTransformMatrix hands back the batch's own matrix, and setTransformMatrix
        // copies into it, so nothing here has to own a new one.
        saved.set(batch.getTransformMatrix());
        scratch.set(saved).translate(spriteX, spriteY, 0f);
        scratch.scale(mirror ? -scale : scale, scale, 1f);
        batch.setTransformMatrix(scratch);
    }

    void end(Batch batch) {
        batch.setTransformMatrix(saved);
    }
}
