package views.gdx.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The seam between what the model SAYS and what the board SHOWS.
//
// Every world effect here is raised by matching a sentence the model wrote for the terminal build. That
// is a good trade -- it costs the model nothing -- but it fails silently in one direction: reword
// "A zombie claws up from a necromancy grave at (6, 2)." in EnvironmentSystem and the tile simply stops
// opening, with no exception, no log line and nothing on screen to say why. A screenshot of a correct
// frame and a screenshot of a broken one differ only by an effect that was never raised.
//
// So the sentences are pinned here, built the way the model builds them rather than copied by eye.
class WeatherEffectsTest {

    // Constructed with no sprites and no geometry on purpose: onEvent only parses and queues, and
    // nothing is asked of either until draw(). That is what makes this reachable without a GL context.
    private static WeatherEffects effects() {
        return new WeatherEffects(null, null);
    }

    // Assembled exactly as EnvironmentSystem.applyNecromancy assembles it -- column first, then row.
    private static String necromancyLine(int col, int row) {
        return "A zombie claws up from a necromancy grave at (" + col + ", " + row + ").";
    }

    @Test
    void aNecromancyRiseOpensTheTileItNames() {
        WeatherEffects effects = effects();

        effects.onEvent(necromancyLine(6, 2));

        // Two halves: the dirt coming up and the beam standing in it.
        assertEquals(2, effects.activeCount());
        assertTrue(effects.hasEffectAt("DIRT_SPAWN_DIRT", 2, 6));
        assertTrue(effects.hasEffectAt("TOMBSTONE_DARK_SPAWN_EFFECT", 2, 6));
    }

    // Column and row the right way round. Getting these swapped draws the burst on a real tile, in the
    // wrong place, which is the one failure mode a screenshot cannot rule out.
    @Test
    void theColumnComesFirstAndTheRowSecond() {
        WeatherEffects effects = effects();

        effects.onEvent(necromancyLine(0, 4));

        assertTrue(effects.hasEffectAt("TOMBSTONE_DARK_SPAWN_EFFECT", 4, 0));
    }

    @Test
    void everyNecromancyTileThatFiresGetsItsOwnBurst() {
        WeatherEffects effects = effects();

        effects.onEvent(necromancyLine(6, 0));
        effects.onEvent(necromancyLine(6, 2));

        assertEquals(4, effects.activeCount());
        assertTrue(effects.hasEffectAt("TOMBSTONE_DARK_SPAWN_EFFECT", 0, 6));
        assertTrue(effects.hasEffectAt("TOMBSTONE_DARK_SPAWN_EFFECT", 2, 6));
    }

    // Big Wave Beach's ambush, which the model narrates in the same shape as the Dark Ages one.
    @Test
    void aLowTideRiseBreaksTheSurfaceOnTheTileItNames() {
        WeatherEffects effects = effects();

        effects.onEvent("A zombie surfaces from the low tide at (7, 3).");

        assertEquals(2, effects.activeCount());
        assertTrue(effects.hasEffectAt("WATER_ZOMBIE_RIPPLE", 3, 7));
        assertTrue(effects.hasEffectAt("WATER_SPLASH", 3, 7));
    }

    // The two ambushes must not be confused for one another: they are different worlds, different art,
    // and only one of them is ever on the board.
    @Test
    void theTwoAmbushesRaiseDifferentArt() {
        WeatherEffects dark = effects();
        dark.onEvent(necromancyLine(6, 2));
        assertTrue(dark.hasEffectAt("TOMBSTONE_DARK_SPAWN_EFFECT", 2, 6));
        assertFalse(dark.hasEffectAt("WATER_SPLASH", 2, 6));

        WeatherEffects beach = effects();
        beach.onEvent("A zombie surfaces from the low tide at (6, 2).");
        assertTrue(beach.hasEffectAt("WATER_SPLASH", 2, 6));
        assertFalse(beach.hasEffectAt("TOMBSTONE_DARK_SPAWN_EFFECT", 2, 6));
    }

    // The other two sentences this class reads, kept honest alongside the new ones.
    @Test
    void theFreezingWindAndTheTornadoStillMatch() {
        WeatherEffects wind = effects();
        wind.onEvent("A freezing wind sweeps through row 3.");
        // Two layers of the same gust, stacked at different points of the clip: one pass of it is a
        // haze on a world already made of ice.
        assertEquals(2, wind.activeCount());

        WeatherEffects storm = effects();
        storm.onEvent("The tornado drops ZombieDefault into lane 2, 3 column(s) past the edge.");
        assertEquals(2, storm.activeCount());   // both halves of the sandstorm
    }

    @Test
    void anythingElseIsIgnored() {
        WeatherEffects effects = effects();

        effects.onEvent(null);
        effects.onEvent("");
        effects.onEvent("Wave 1 started.");
        effects.onEvent("The tide rises and floods column 7.");
        effects.onEvent("A zombie claws up from a necromancy grave at (6, 2)");   // no full stop

        assertEquals(0, effects.activeCount());
    }

}
