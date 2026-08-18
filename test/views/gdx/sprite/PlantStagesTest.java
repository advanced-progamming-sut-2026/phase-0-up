package views.gdx.sprite;

import com.badlogic.gdx.math.Rectangle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Guards which clip an entity RESTS on, because getting it wrong is silent and looks like a layout bug.
//
// Every place that draws an entity outside the lawn -- a seed card, an almanac tile, the live view --
// scales the art to fit the box of its resting clip. bounds() unions every frame of a clip, so a walk
// cycle's box is the whole stride: ZombieNewspaper's "walk" is 809x378 against idle_newspaper's 175x204.
// Resting on the wrong clip therefore does not merely show the wrong pose, it draws the entity at less
// than half the size of its neighbours, which is exactly what happened -- for months, unnoticed, because
// nothing throws and nothing logs.
//
// EntitySprite is an interface, so these run with no libGDX or libPVZ runtime behind them.
class PlantStagesTest {

    // Only the two methods PlantStages actually asks about. Everything else throws, so a change that
    // starts consulting bounds or draws anything fails loudly here rather than quietly in a screenshot.
    private static final class FakeSprite implements EntitySprite {
        private final Set<String> clips = new LinkedHashSet<>();

        FakeSprite(String... names) {
            clips.addAll(List.of(names));
        }

        @Override
        public boolean hasClip(String clip) {
            return clips.contains(clip);
        }

        @Override
        public Set<String> clips() {
            return clips;
        }

        @Override
        public void draw(com.badlogic.gdx.graphics.g2d.Batch batch, String clip, float stateTime,
                         float x, float y, boolean faceRight) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void draw(com.badlogic.gdx.graphics.g2d.Batch batch, String clip, float stateTime,
                         float x, float y, boolean faceRight, java.util.Map<String, Boolean> parts) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public boolean isAnimated() {
            return true;
        }

        @Override
        public boolean hasPart(String partName) {
            return false;
        }

        @Override
        public Rectangle bounds(String clip) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Rectangle visibleBounds(String clip, Set<String> hidden) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Rectangle partBounds(String clip, String partName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Rectangle[] partBoundsByFrame(String clip, String partName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Rectangle anchorBounds() {
            throw new UnsupportedOperationException();
        }

        @Override
        public float clipDuration(String clip) {
            return 0f;
        }
    }

    @Test
    @DisplayName("an idle named after a held prop beats the walk cycle")
    void suffixedIdleBeatsWalk() {
        // ZombieNewspaper's real clip list, in the order the dump gives it. It has no bare "idle", and
        // "walk" is in PlantStages' last-resort list -- so before the idle-prefixed pass it rested on
        // its walk cycle.
        EntitySprite newspaper = new FakeSprite("idle_newspaper", "walk_newspaper", "walk",
                "eat_newspaper", "newspaper_defeat", "eat", "die", "particles");
        assertEquals("idle_newspaper", PlantStages.restingClip(newspaper));
    }

    @Test
    @DisplayName("a plain idle still wins over any suffixed one")
    void plainIdleWins() {
        EntitySprite peashooter = new FakeSprite("idle", "idle_special", "walk", "attack");
        assertEquals("idle", PlantStages.restingClip(peashooter));
    }

    @Test
    @DisplayName("a staged idle still wins, so growing plants are unaffected")
    void stagedIdleWins() {
        EntitySprite sunshroom = new FakeSprite("idle_stage1", "idle_stage2", "idle_puff", "walk");
        assertEquals("idle_stage1", PlantStages.restingClip(sunshroom));
    }

    @Test
    @DisplayName("an entity with only action clips still falls back rather than drawing nothing")
    void actionOnlyFallsBack() {
        // Grave Buster: attack clips and nothing else. The last resort must still fire.
        EntitySprite graveBuster = new FakeSprite("attack", "attack2");
        assertEquals("attack", PlantStages.restingClip(graveBuster));
    }

    @Test
    @DisplayName("every idle variant is offered, not just the first")
    void allIdleVariantsListed() {
        EntitySprite sprite = new FakeSprite("idle_newspaper", "idle_newspaper2", "walk");
        List<String> variants = PlantStages.idleVariants(sprite, 0, 1);
        assertEquals(List.of("idle_newspaper", "idle_newspaper2"), variants);
        assertTrue(variants.stream().noneMatch("walk"::equals));
    }
}
