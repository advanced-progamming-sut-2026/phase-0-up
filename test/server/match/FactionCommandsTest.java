package server.match;

import models.game.Faction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import utils.regex.InGameRegex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The rule that stops one player playing the other's half.
//
// Worth a unit test of its own, separate from MatchRunnerTest's socket-level checks, because the
// interesting property is about the commands that are NOT listed. A socket test can only ask about
// commands somebody thought to try; this one can ask about all of them at once.
class FactionCommandsTest {

    @Test
    @DisplayName("each side may do its own job")
    void eachSideMayPlayItsOwnHalf() {
        assertTrue(FactionCommands.isAllowed(Faction.PLANTS,
                "plant plant -t Sunflower -l (0, 2)"));
        assertTrue(FactionCommands.isAllowed(Faction.PLANTS, "pluck plant -l (0, 2)"));
        assertTrue(FactionCommands.isAllowed(Faction.PLANTS, "feed plant -l (0, 2)"));
        assertTrue(FactionCommands.isAllowed(Faction.PLANTS, "collect sun -l (3, 1)"));
        assertTrue(FactionCommands.isAllowed(Faction.ZOMBIES, "summon -t ZombieImp -l (8, 0)"));
    }

    @Test
    @DisplayName("and neither may do the other's")
    void neitherSideMayPlayTheOthers() {
        assertFalse(FactionCommands.isAllowed(Faction.ZOMBIES,
                "plant plant -t Sunflower -l (0, 2)"));
        assertFalse(FactionCommands.isAllowed(Faction.ZOMBIES, "collect sun -l (3, 1)"));
        assertFalse(FactionCommands.isAllowed(Faction.PLANTS, "summon -t ZombieImp -l (8, 0)"));
    }

    @Test
    @DisplayName("the refusal says which half of the lawn the command belongs to")
    void refusalsExplainThemselves() {
        String toZombiePlayer = FactionCommands.refusalFor(Faction.ZOMBIES,
                "plant plant -t Sunflower -l (0, 2)");
        assertNotNull(toZombiePlayer);
        assertTrue(toZombiePlayer.contains("plant player"), toZombiePlayer);

        String toPlantPlayer = FactionCommands.refusalFor(Faction.PLANTS,
                "summon -t ZombieImp -l (8, 0)");
        assertNotNull(toPlantPlayer);
        assertTrue(toPlantPlayer.contains("zombie player"), toPlantPlayer);
    }

    // The load-bearing one.
    //
    // A whitelist fails CLOSED: a command added to InGameRegex later is refused by both factions until
    // somebody deliberately lists it. This asserts that property directly rather than trusting it --
    // if the implementation is ever turned into a blacklist, every future command silently becomes
    // legal for both players and nothing else in the suite would notice.
    @Test
    @DisplayName("every command nobody listed is refused to both players")
    void anythingUnlistedIsRefusedToEverybody() {
        for (InGameRegex command : InGameRegex.values()) {
            boolean plants = FactionCommands.isAllowed(Faction.PLANTS, sample(command));
            boolean zombies = FactionCommands.isAllowed(Faction.ZOMBIES, sample(command));
            assertFalse(plants && zombies,
                    command + " is allowed to BOTH players, which no command should be");
        }
    }

    @Test
    @DisplayName("cheats, the clock and the other mini-games' verbs belong to nobody")
    void nobodyMayCheatOrTouchTheClock() {
        for (String command : new String[] {
                "release the nuke",
                "cheat add -n 500 suns",
                "cheat add-plant-food",
                "cheat remove-cooldown",
                "cheat spawn-zombie -t ZombieGargantuar -l (8, 0)",
                "advance time -t 100 ticks",
                // Leaving is MATCH_LEAVE_REQ, which forfeits properly. Through here it would end the
                // shared session out from under the other player.
                "exit game",
                "swap -l (0, 0) (1, 0)",
                "upgrade -t Peashooter",
                "bowl -t WALLNUT -l (0, 2)",
                "break vase -l (3, 3)",
                "collect seed -l (3, 3)"}) {
            for (Faction faction : Faction.values()) {
                assertFalse(FactionCommands.isAllowed(faction, command),
                        faction + " must not be able to run \"" + command + "\"");
            }
        }
    }

    @Test
    @DisplayName("nonsense and a player who is in no match are both refused rather than crashing")
    void junkIsRefused() {
        assertNotNull(FactionCommands.refusalFor(Faction.PLANTS, "make me a sandwich"));
        assertNotNull(FactionCommands.refusalFor(Faction.PLANTS, ""));
        assertNotNull(FactionCommands.refusalFor(Faction.PLANTS, null));
        assertNotNull(FactionCommands.refusalFor(null, "plant plant -t Sunflower -l (0, 2)"));
    }

    @Test
    @DisplayName("a command is identified by the same table the engine dispatches on")
    void parsingMatchesTheEngine() {
        assertEquals(InGameRegex.PLANT_SEED,
                FactionCommands.parse("plant plant -t Snow Pea -l (2, 3)"));
        assertEquals(InGameRegex.SUMMON_ZOMBIE,
                FactionCommands.parse("summon -t ZombieGargantuar -l (8, 4)"));
        // Spacing is free, exactly as it is at the prompt -- Regex.getMatcher trims and every pattern
        // separates tokens with \s+. A whitelist that matched on prefixes would let a differently
        // spaced command walk straight past it.
        assertEquals(InGameRegex.PLUCK_PLANT,
                FactionCommands.parse("  pluck   plant  -l ( 1 , 2 )  "));
        assertNull(FactionCommands.parse("plant a tree"));
    }

    // A valid instance of each command, so the sweep above is testing real strings rather than the
    // enum's name.
    private static String sample(InGameRegex command) {
        return switch (command) {
            case COLLECT_SUN -> "collect sun -l (3, 1)";
            case SHOW_SUN_AMOUNT -> "show sun amount";
            case CHEAT_ADD_SUN -> "cheat add -n 50 suns";
            case ADVANCE_TIME -> "advance time -t 10 ticks";
            case PLANT_SEED -> "plant plant -t Sunflower -l (0, 2)";
            case PLUCK_PLANT -> "pluck plant -l (0, 2)";
            case FEED_PLANT -> "feed plant -l (0, 2)";
            case CHEAT_REMOVE_COOLDOWN -> "cheat remove-cooldown";
            case CHEAT_ADD_PLANT_FOOD -> "cheat add-plant-food";
            case RELEASE_THE_NUKE -> "release the nuke";
            case SHOW_MAP -> "show map";
            case SHOW_PLANTS_STATUS -> "show plants status";
            case SHOW_TILE_STATUS -> "show tile status -l (1, 1)";
            case BREAK_VASE -> "break vase -l (3, 3)";
            case COLLECT_SEED -> "collect seed -l (3, 3)";
            case BOWL_NUT -> "bowl -t WALLNUT -l (0, 2)";
            case SUMMON_ZOMBIE -> "summon -t ZombieImp -l (8, 0)";
            case ZOMBIES_INFO -> "zombies info";
            case CHEAT_SPAWN_ZOMBIE -> "cheat spawn-zombie -t ZombieImp -l (8, 0)";
            case SWAP_PLANTS -> "swap -l (0, 0) (1, 0)";
            case EXIT_GAME -> "exit game";
            case UPGRADE_PLANT -> "upgrade -t Peashooter";
        };
    }
}
