package controllers.commands.seedselection;

import factories.LevelFactory;
import factories.PlantFactory;
import models.entities.plants.Plant;
import models.entities.projectiles.Projectile;
import models.entities.projectiles.Trajectory;
import models.game.GameSession;
import models.game.Level;
import models.map.Cell;
import models.map.Terrains.NormalGrave;
import models.map.Terrains.GraveTerrain;
import models.user.Profile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import utils.gameinitializers.GameInitializer;
import views.renderers.MenuRenderer.PlantMenuRenderer;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Two rules a screenshot cannot check, both reported from play.
//
//   1. Upgrading a plant must not lock it. The upgrade SPENDS five seed packets, and ownership used to
//      be read off the packet count -- so a plant sitting on exactly five became unusable the moment a
//      player invested in it.
//   2. A lobbed shooter must break graves when its lane is empty, and must still arc over them at a
//      zombie when one is there.
class SeedLockAndLobTest {

    private static final String LEVEL_ID = "s1l1";

    @BeforeAll
    static void loadGameData() {
        new GameInitializer().loadAllData();
    }

    private static GameSession session() {
        Level level = LevelFactory.createLevel(LEVEL_ID);
        return new GameSession(new Profile(), level);
    }

    // Records the renderer method the command reached for. The interface has nineteen of them and this
    // test cares only about WHICH one fired, so a proxy is shorter than a stub and cannot drift as the
    // interface grows.
    private static final class Calls {
        private String last = "";

        PlantMenuRenderer renderer() {
            return (PlantMenuRenderer) Proxy.newProxyInstance(
                    PlantMenuRenderer.class.getClassLoader(),
                    new Class<?>[] {PlantMenuRenderer.class},
                    (proxy, method, args) -> {
                        last = method.getName();
                        return null;
                    });
        }
    }

    // ---- 1. the upgrade lock ------------------------------------------------------------------

    @Test
    void anUnlockedPlantWithNoSeedPacketsLeftIsStillSelectable() {
        GameSession gameSession = session();
        Profile profile = gameSession.getPlayer();
        String plant = gameSession.getLevel().getAvailablePlants().get(0);

        // Exactly the state CollectionSystem.upgradePlant leaves behind when the player had five.
        profile.unlockPlant(plant);
        profile.getOwnedSeedPackets().put(plant.toLowerCase().trim(), 0);

        Calls calls = new Calls();
        new ToggleSeedCommand(ToggleAction.ADD, plant, gameSession, calls.renderer()).execute();

        assertNotEquals("isLocked", calls.last,
                "a plant the player owns must not read as locked just because its packets were spent");
        assertEquals("successfulAdd", calls.last);
        assertTrue(gameSession.isSeedSelected(plant));
    }

    @Test
    void aPlantThePlayerDoesNotOwnIsStillRefused() {
        GameSession gameSession = session();
        Profile profile = gameSession.getPlayer();
        String plant = gameSession.getLevel().getAvailablePlants().get(0);
        profile.getUnlockedPlants().removeIf(owned -> owned.equalsIgnoreCase(plant));

        Calls calls = new Calls();
        new ToggleSeedCommand(ToggleAction.ADD, plant, gameSession, calls.renderer()).execute();

        assertEquals("isLocked", calls.last);
        assertFalse(gameSession.isSeedSelected(plant));
    }

    // ---- 2. lobbed shots and graves -------------------------------------------------------------

    private static final int ROW = 2;
    private static final int PLANT_COL = 1;
    private static final int GRAVE_COL = 5;

    // Runs the lobber until it lets a shot go, and hands back the first projectile in its lane.
    private static Projectile fireOnce(GameSession gameSession, Plant lobber) {
        for (int tick = 0; tick < 200; tick++) {
            lobber.update(gameSession);
            List<Projectile> shots = gameSession.getMap().getRow(ROW).getActiveProjectiles();
            if (shots != null && !shots.isEmpty()) {
                return shots.get(0);
            }
        }
        return null;
    }

    private static Plant plantLobber(GameSession gameSession) {
        Plant lobber = PlantFactory.createPlant("Kernel-pult", 1, PLANT_COL, ROW);
        gameSession.getMap().getRow(ROW).cellAt(PLANT_COL).addPlant(lobber);
        return lobber;
    }

    private static NormalGrave placeGrave(GameSession gameSession) {
        Cell cell = gameSession.getMap().getRow(ROW).cellAt(GRAVE_COL);
        NormalGrave grave = new NormalGrave(gameSession, cell);
        cell.addTerrain(grave);
        return grave;
    }

    @Test
    void anEmptyLaneMakesTheLobAimAtTheGraveAndBreakIt() {
        GameSession gameSession = session();
        Plant lobber = plantLobber(gameSession);
        GraveTerrain grave = placeGrave(gameSession);
        int fullHp = grave.getHp();

        Projectile shot = fireOnce(gameSession, lobber);
        assertNotNull(shot, "a lobber with a grave ahead and no zombies must open fire");
        assertEquals(Trajectory.LOBBED, shot.getTrajectory());
        assertTrue(shot.isTerrainSeeking(),
                "with no zombie in the lane the shot must be aimed at the grave");

        for (int tick = 0; tick < 200 && !shot.isDestroyed(); tick++) {
            shot.update(gameSession);
        }
        assertTrue(grave.getHp() < fullHp,
                "the grave must take the hit -- hp went " + fullHp + " to " + grave.getHp());
        assertTrue(shot.isDestroyed(), "the shot must stop at the grave rather than slide past it");
    }

    @Test
    void aZombieInTheLaneWinsAndTheShotArcsOverTheGrave() {
        GameSession gameSession = session();
        Plant lobber = plantLobber(gameSession);
        GraveTerrain grave = placeGrave(gameSession);
        int fullHp = grave.getHp();

        // Beyond the grave, so "it arced over" and "it fell short" are different outcomes.
        gameSession.spawnZombieCheat("ZombieDefault", 8, ROW);

        Projectile shot = fireOnce(gameSession, lobber);
        assertNotNull(shot, "a lobber with a zombie ahead must open fire");
        assertFalse(shot.isTerrainSeeking(),
                "a zombie in the lane must win: the shot arcs over the grave, it does not target it");

        for (int tick = 0; tick < 40 && !shot.isDestroyed(); tick++) {
            shot.update(gameSession);
            if (shot.getX() > GRAVE_COL + 0.6) {
                break;
            }
        }
        assertEquals(fullHp, grave.getHp(), "a lob passing over a grave must not damage it");
    }
}
