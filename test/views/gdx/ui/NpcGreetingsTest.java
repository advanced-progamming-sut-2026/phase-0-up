package views.gdx.ui;

import models.game.EnvironmentType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The pre-level greetings -- the one set of lines in this build that a person wrote rather than the
// model emitting them.
//
// That is what makes them worth a test. Every other NPC line is a model sentence and is therefore
// covered by whatever covers the model; these have no such backstop, and the two ways they can go wrong
// are both silent. A world with no line at all shows an empty box, and a non-ASCII character renders as
// a blank square in the skin's bitmap font -- neither throws, and a screenshot of one level says
// nothing about the other seven.
class NpcGreetingsTest {

    // Every world, opening level and a couple of later ones.
    @Test
    void everyWorldHasSomethingToSayAtEveryLevel() {
        for (EnvironmentType world : EnvironmentType.values()) {
            for (int level = 1; level <= 5; level++) {
                NpcGreetings.Greeting greeting = NpcGreetings.forLevel(world, level);
                assertNotNull(greeting, world + " level " + level);
                assertNotNull(greeting.speaker(), world + " level " + level);
                assertFalse(greeting.line().isBlank(), world + " level " + level);
            }
        }
    }

    // Dave opens a world and never explains it; Penny takes every level after that.
    @Test
    void daveOpensAWorldAndPennyTakesTheRest() {
        for (EnvironmentType world : EnvironmentType.values()) {
            assertEquals(NpcDialogueBox.Speaker.DAVE, NpcGreetings.forLevel(world, 1).speaker(), "" + world);
            assertEquals(NpcDialogueBox.Speaker.PENNY, NpcGreetings.forLevel(world, 2).speaker(), "" + world);
            assertEquals(NpcDialogueBox.Speaker.PENNY, NpcGreetings.forLevel(world, 4).speaker(), "" + world);
        }
    }

    // Penny alternates, so a five-level chapter is not one sentence five times.
    @Test
    void pennyDoesNotRepeatHerselfOnConsecutiveLevels() {
        for (EnvironmentType world : EnvironmentType.values()) {
            String second = NpcGreetings.forLevel(world, 2).line();
            String third = NpcGreetings.forLevel(world, 3).line();
            assertFalse(second.equals(third), "" + world);
        }
    }

    // The skin's fonts are bitmap sets generated from an ASCII page: anything outside it draws as a
    // blank box. An em dash pasted in from a document is the realistic way this gets broken.
    @Test
    void everyLineIsPlainAscii() {
        for (EnvironmentType world : EnvironmentType.values()) {
            for (int level = 1; level <= 5; level++) {
                String line = NpcGreetings.forLevel(world, level).line();
                for (char c : line.toCharArray()) {
                    assertTrue(c >= 0x20 && c < 0x7F,
                            world + " level " + level + " has a non-ASCII character: " + (int) c);
                }
            }
        }
    }

    @Test
    void anUnknownWorldSaysNothingRatherThanCrashing() {
        assertNull(NpcGreetings.forLevel(null, 1));
    }
}
