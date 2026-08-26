package models.entities.plants.abilities.triggers;

import factories.LevelFactory;
import factories.PlantFactory;
import models.entities.plants.Plant;
import models.entities.projectiles.Projectile;
import models.game.GameSession;
import models.game.Level;
import models.map.Cell;
import models.map.Terrains.FrozenTerrain;
import models.map.Terrains.NormalGrave;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import utils.gameinitializers.GameInitializer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// "Shoot the thing in the way" -- and an ice block is a thing in the way.
//
// Frostbite Caves walls lanes off with ice blocks exactly as Ancient Egypt walls them off with
// headstones, and the two were half-built in opposite directions. Shots have always DAMAGED an ice
// block (FrozenTerrain sets blocksProjectiles, and Projectile.handleTerrainCollisions damages anything
// carrying that flag) -- but the firing condition was `instanceof GraveTerrain`, so no plant would ever
// aim at one with the lane empty. A lane sealed by ice could only be opened by fire or by waiting out
// the melt, and nothing on screen explained why the peashooter behind it had stopped shooting.
//
// The predicate is now the blocking flag itself, so the two cannot drift apart again. Pinned here
// because the failure is silent in the worst way: the plant is fine, the shot is fine, and the plant
// simply never fires.
class ObstacleSightTest {

    private static final String LEVEL_ID = "s1l1";
    private static final int ROW = 2;
    private static final int PLANT_COL = 1;
    private static final int OBSTACLE_COL = 5;

    @BeforeAll
    static void loadGameData() {
        new GameInitializer().loadAllData();
    }

    private static GameSession session() {
        Level level = LevelFactory.createLevel(LEVEL_ID);
        return new GameSession(new models.user.Profile(), level);
    }

    private static Cell obstacleCell(GameSession gameSession) {
        return gameSession.getMap().getRow(ROW).cellAt(OBSTACLE_COL);
    }

    // A straight shooter rather than a lobber: this is about the TRIGGER, and a direct shot reaching
    // the block is the plainest evidence that the plant decided to fire at all.
    private static Plant plantShooter(GameSession gameSession) {
        Plant shooter = PlantFactory.createPlant("Peashooter", 1, PLANT_COL, ROW);
        gameSession.getMap().getRow(ROW).cellAt(PLANT_COL).addPlant(shooter);
        return shooter;
    }

    private static Projectile fireOnce(GameSession gameSession, Plant shooter) {
        for (int tick = 0; tick < 200; tick++) {
            shooter.update(gameSession);
            List<Projectile> shots = gameSession.getMap().getRow(ROW).getActiveProjectiles();
            if (shots != null && !shots.isEmpty()) {
                return shots.get(0);
            }
        }
        return null;
    }

    // The bug, stated directly.
    @Test
    void aShooterOpensFireOnAnIceBlockWithNoZombieInTheLane() {
        GameSession gameSession = session();
        Plant shooter = plantShooter(gameSession);
        obstacleCell(gameSession).addTerrain(new FrozenTerrain());

        assertTrue(ObstacleSight.obstacleAhead(shooter, gameSession, 0.0),
                "an ice block ahead is something to shoot at");
        assertNotNull(fireOnce(gameSession, shooter),
                "a shooter with an ice block ahead and no zombies must open fire");
    }

    // The shot has always been able to hurt it; what was missing was anyone firing one.
    @Test
    void theShotBreaksTheBlockDown() {
        GameSession gameSession = session();
        Plant shooter = plantShooter(gameSession);
        FrozenTerrain block = new FrozenTerrain();
        obstacleCell(gameSession).addTerrain(block);

        Projectile shot = fireOnce(gameSession, shooter);
        assertNotNull(shot);
        for (int tick = 0; tick < 200 && !shot.isDestroyed(); tick++) {
            shot.update(gameSession);
        }
        assertTrue(shot.isDestroyed(), "the shot must stop at the block rather than sail through it");
    }

    // Graves still work, and for the same reason -- the predicate did not become "ice", it became
    // "anything a shot can hit".
    @Test
    void aGraveIsStillSeen() {
        GameSession gameSession = session();
        Plant shooter = plantShooter(gameSession);
        Cell cell = obstacleCell(gameSession);
        cell.addTerrain(new NormalGrave(gameSession, cell));

        assertTrue(ObstacleSight.obstacleAhead(shooter, gameSession, 0.0));
    }

    // Terrain a shot passes straight through is not a reason to fire. Low sand and cursed ground are
    // markers on the floor, not walls, and a plant shooting at the ground would never stop.
    @Test
    void groundThatBlocksNothingIsNotATarget() {
        GameSession gameSession = session();
        Plant shooter = plantShooter(gameSession);
        obstacleCell(gameSession).addTerrain(
                new models.map.Terrains.NecromancyTerrain(gameSession, obstacleCell(gameSession)));

        assertFalse(ObstacleSight.obstacleAhead(shooter, gameSession, 0.0),
                "a necromancy tile does not block shots, so there is nothing to shoot");
    }

    @Test
    void anEmptyLaneOffersNothingToShootAt() {
        GameSession gameSession = session();
        Plant shooter = plantShooter(gameSession);

        assertFalse(ObstacleSight.obstacleAhead(shooter, gameSession, 0.0));
    }

    // Behind is behind: Split Pea's rear barrel reads the same list from the other side.
    @Test
    void anObstacleBehindIsOnlySeenLookingBackwards() {
        GameSession gameSession = session();
        Plant shooter = PlantFactory.createPlant("Peashooter", 1, OBSTACLE_COL + 1, ROW);
        gameSession.getMap().getRow(ROW).cellAt(OBSTACLE_COL + 1).addPlant(shooter);
        obstacleCell(gameSession).addTerrain(new FrozenTerrain());

        assertTrue(ObstacleSight.obstacleBehind(shooter, gameSession));
        assertFalse(ObstacleSight.obstacleAhead(shooter, gameSession, 0.0));
    }
}
