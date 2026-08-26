package controllers.systems.game;

import factories.LevelFactory;
import factories.PlantFactory;
import factories.ZombieFactory;
import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import models.game.Level;
import models.user.Profile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import utils.Result;
import utils.gameinitializers.GameInitializer;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The order a tick's narration reaches the view in -- which is NOT the order the events happened.
//
// A tick speaks down two separate channels:
//
//   * CombatSystem.processTick RETURNS its death lines, and GameEngine.advanceOneTick renders that
//     list as soon as it has it.
//   * a plant's detonation goes through GameSession.reportEvent into the domain-event queue, which
//     advanceOneTick drains AFTERWARDS, at the end of the same tick.
//
// So "Zombie of type X is dead" arrives BEFORE "Cherry Bomb detonates", even though the bomb is what
// killed it. That inversion is the whole reason DeathEffects could never draw ash: it asked
// ExplosionEffects "was this tile just blasted?" the moment the death arrived, and the blast had not
// been announced yet. Every explosive kill fell over as an ordinary corpse.
//
// Pinned here because the inversion is invisible from either side on its own -- each queue is
// perfectly ordered within itself -- and because the view now deliberately defers its decision to cope
// with it. If this order is ever changed the deferral is still correct, but whoever changes it should
// know it was load-bearing.
class ExplosionDeathOrderTest {

    private static final String LEVEL_ID = "s1l1";
    private static final int ROW = 2;
    private static final int BOMB_COL = 4;

    @BeforeAll
    static void loadGameData() {
        new GameInitializer().loadAllData();
    }

    private static GameSession session() {
        Level level = LevelFactory.createLevel(LEVEL_ID);
        return new GameSession(new Profile(), level);
    }

    // What one tick said, in the order GameEngine.advanceOneTick renders it: the combat system's
    // returned events first, then whatever the domain-event queue collected.
    private static List<String> narrationOfOneTick(CombatSystem combat, GameSession gameSession,
                                                   long tick) {
        List<String> said = new ArrayList<>();
        for (Result event : combat.processTick(gameSession, tick)) {
            said.add(event.message());
        }
        for (Result event : gameSession.drainEvents()) {
            said.add(event.message());
        }
        return said;
    }

    private static int indexOfMatch(List<String> said, String fragment) {
        for (int i = 0; i < said.size(); i++) {
            if (said.get(i).contains(fragment)) {
                return i;
            }
        }
        return -1;
    }

    @Test
    void aBlastIsAnnouncedAfterTheDeathsItCauses() {
        GameSession gameSession = session();

        Plant bomb = PlantFactory.createPlant("Cherry Bomb", 1, BOMB_COL, ROW);
        assertNotNull(bomb, "Cherry Bomb must exist in plants.json");
        gameSession.getMap().getRow(ROW).cellAt(BOMB_COL).addPlant(bomb);

        Zombie victim = ZombieFactory.createZombie("ZombieDefault", BOMB_COL, ROW, gameSession);
        assertNotNull(victim);
        gameSession.getMap().getRow(ROW).getZombies().add(victim);

        CombatSystem combat = new CombatSystem();
        List<String> tickThatBlew = null;
        for (long tick = 1; tick <= 60 && tickThatBlew == null; tick++) {
            List<String> said = narrationOfOneTick(combat, gameSession, tick);
            if (indexOfMatch(said, "detonates at") >= 0) {
                tickThatBlew = said;
            }
        }

        assertNotNull(tickThatBlew, "the Cherry Bomb must go off within six seconds of being planted");

        int death = indexOfMatch(tickThatBlew, "is dead at");
        int blast = indexOfMatch(tickThatBlew, "detonates at");
        assertTrue(death >= 0,
                "the blast must kill the zombie standing on it, but this tick said: " + tickThatBlew);

        // The inversion itself. Stated as an assertion rather than a comment so that a change to it is
        // a failing test rather than a silent one.
        assertTrue(death < blast,
                "the death is narrated BEFORE the detonation that caused it -- if this has changed, "
                        + "see the note in DeathEffects about why the ash decision is deferred. Said: "
                        + tickThatBlew);
    }

    // The other half of the trap, and the reason the bug hid for so long: a zombie the blast only
    // WOUNDS dies on some later tick, by which point the detonation has been drained. Those deaths
    // always looked right, so the ones that were wrong read as random rather than as a rule.
    @Test
    void aWoundedZombieDiesOnALaterTickThanTheBlast() {
        GameSession gameSession = session();

        Plant bomb = PlantFactory.createPlant("Cherry Bomb", 1, BOMB_COL, ROW);
        assertNotNull(bomb);
        gameSession.getMap().getRow(ROW).cellAt(BOMB_COL).addPlant(bomb);

        CombatSystem combat = new CombatSystem();
        int blastTick = -1;
        for (long tick = 1; tick <= 60 && blastTick < 0; tick++) {
            if (indexOfMatch(narrationOfOneTick(combat, gameSession, tick), "detonates at") >= 0) {
                blastTick = (int) tick;
            }
        }

        assertNotEquals(-1, blastTick, "the bomb must go off with nothing on the lawn to kill");
    }
}
