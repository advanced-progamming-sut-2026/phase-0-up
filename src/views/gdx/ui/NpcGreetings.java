package views.gdx.ui;

import models.game.EnvironmentType;
import views.gdx.ui.NpcDialogueBox.Speaker;

// What an NPC says as a level opens.
//
// ## This is the one place lines are WRITTEN
//
// Everything else NpcDialogueBox says is a sentence the model already emits, and NpcLines exists to
// route those rather than to author any (see its header). That rule holds because those moments -- a
// mode's rules, the final wave -- are things the model genuinely has to say, and saying them twice in
// two different wordings is how two front ends drift apart.
//
// A greeting has no such sentence, because it is not a fact about the game: the terminal build opens a
// level by printing the board. So these lines are the view's own, and kept honest a different way --
// every one of them is about a mechanic that is really in this build, so the flavour doubles as the
// only tutorial these worlds get. Penny's advisories name the exact tiles TerrainRenderer now marks.
//
// ## Who speaks, and when
//
// Dave opens a world -- level 1 of a chapter is the only time a player has not seen it before, and Dave
// is the one who does not explain things. Every level after that is Penny, alternating between two
// advisories so a five-level chapter is not the same sentence five times.
//
// A special mode has its own opening banner and Penny already delivers it (NpcLines). GameScreen
// therefore offers a greeting only when nothing is already being said, so a Vasebreaker board explains
// Vasebreaker rather than the weather.
public final class NpcGreetings {

    /** Who says it, and what. */
    public record Greeting(Speaker speaker, String line) { }

    private NpcGreetings() {
    }

    // Dave arriving in a new world. No advice in any of them, deliberately -- that is Penny's job, and
    // Dave being useless is the joke.
    private static String daveOpens(EnvironmentType world) {
        return switch (world) {
            case ANCIENT_EGYPT ->
                    "Egypt! I parked my car here in 3000 BC. If you find it, the keys are under the "
                            + "pyramid. WABBY WABBO!";
            case FROSTBITE_CAVES ->
                    "Brrr! I left a taco in these caves a thousand years ago. It is still in here "
                            + "somewhere. Do not eat it. Or do. You are a grown-up.";
            case BIG_WAVE_BEACH ->
                    "The beach! Sun, sand, and zombies who swim better than you do. I brought a "
                            + "snorkel. It is full of chili.";
            case DARK_AGES ->
                    "The Dark Ages! No sun, no dentists, no taco stands. I do not know how anybody "
                            + "lived here, neighbour.";
        };
    }

    // Penny, on the mechanic that will actually kill you in this world. Two per world, alternating on
    // the level number, and every one of them names something the board draws.
    private static String pennyAdvises(EnvironmentType world, int levelNumber) {
        boolean first = levelNumber % 2 == 0;
        return switch (world) {
            case ANCIENT_EGYPT -> first
                    ? "Advisory: gravestones block your shots and cannot be planted on. Grave Buster "
                            + "removes one permanently."
                    : "Advisory: the sandstorm on the final wave drops zombies PAST your front line. "
                            + "Do not leave the back columns empty.";
            case FROSTBITE_CAVES -> first
                    ? "Advisory: a freezing wind sweeps whole rows between waves. Three chills and a "
                            + "plant is frozen solid."
                    : "Advisory: ice blocks hold a plant or a zombie inside them. Fire clears the "
                            + "block; the slider tiles will move whatever is standing on them.";
            case BIG_WAVE_BEACH -> first
                    ? "Advisory: the tide floods one more column each wave. Anything not aquatic and "
                            + "not on a Lily Pad is swept away with it."
                    : "Advisory: the dark, rippling tile is sunken sand. A zombie can surface from "
                            + "underneath it, well behind your line.";
            case DARK_AGES -> first
                    ? "Advisory: no sun falls from this sky. Sun-shroom and Sunflower are your entire "
                            + "supply, so plant them first."
                    : "Advisory: the glowing purple tiles are cursed ground. A zombie can climb out of "
                            + "one at the start of any wave.";
        };
    }

    /**
     * The line for a level opening, or null when there is nothing to say.
     *
     * @param world        the level's season, from its chapter
     * @param levelNumber  the level's number within that chapter, 1-based
     */
    public static Greeting forLevel(EnvironmentType world, int levelNumber) {
        if (world == null) {
            return null;
        }
        if (levelNumber <= 1) {
            return new Greeting(Speaker.DAVE, daveOpens(world));
        }
        return new Greeting(Speaker.PENNY, pennyAdvises(world, levelNumber));
    }
}
