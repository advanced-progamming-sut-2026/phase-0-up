package controllers.systems.game;

import factories.LevelFactory;
import factories.PlantFactory;
import factories.ZombieFactory;
import models.entities.plants.Plant;
import models.entities.projectiles.Element;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import models.game.Level;
import models.map.Cell;
import models.map.Terrains.FrozenTerrain;
import models.user.Profile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import utils.gameinitializers.GameInitializer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Frostbite Caves' two rules about ice, both of which were half-implemented.
//
//   1. Ice does not affect the zombies who live in it. ZombieFactory has always set freezeImmune on
//      every zombie born in this world, and StateComponent has always honoured it -- for FREEZE only.
//      A chill went straight through, so a Snow Pea could still cut a Frostbite zombie to half pace,
//      which is exactly the effect the world is supposed to be immune to.
//
//   2. Fire melts the ice. WarmthAbility does that and Pepper-pult carries it; Torchwood, the other
//      fire plant a player reaches for in this world, did not carry it at all.
//
// Both are invisible in play until you specifically go looking: a chilled Frostbite zombie just walks
// a bit slower than it should, and a Torchwood next to an ice block simply sits there.
class FrostbiteIceTest {

    private static final String FROSTBITE = "s2l1";
    private static final String EGYPT = "s1l1";
    private static final int ROW = 2;
    private static final int PLANT_COL = 3;
    private static final int BLOCK_COL = 4;

    @BeforeAll
    static void loadGameData() {
        new GameInitializer().loadAllData();
    }

    private static GameSession sessionIn(String levelId) {
        Level level = LevelFactory.createLevel(levelId);
        return new GameSession(new Profile(), level);
    }

    private static Zombie spawn(GameSession gameSession) {
        Zombie zombie = ZombieFactory.createZombie("ZombieDefault", 6.5, ROW, gameSession);
        assertNotNull(zombie);
        gameSession.getMap().getRow(ROW).getZombies().add(zombie);
        return zombie;
    }

    // ---- 1. ice does nothing to a Frostbite zombie ----------------------------------------------

    @Test
    void aFrostbiteZombieIsNeitherChilledNorFrozenByAnIceHit() {
        GameSession gameSession = sessionIn(FROSTBITE);
        Zombie zombie = spawn(gameSession);

        Element.ICE.applyOnHit(zombie.getState());
        zombie.getState().applyFreeze(200);

        assertFalse(zombie.getState().isChilled(),
                "ice must not slow a zombie that lives in a cave made of it");
        assertFalse(zombie.getState().isFrozen());
    }

    // The upgrade must not be a way back in: extendChill adds to a chill the hit itself could not apply.
    @Test
    void theSnowPeaUpgradeCannotChillOneEither() {
        GameSession gameSession = sessionIn(FROSTBITE);
        Zombie zombie = spawn(gameSession);

        Element.ICE.applyOnHit(zombie.getState());
        zombie.getState().extendChill(200);

        assertFalse(zombie.getState().isChilled());
    }

    // The immunity is to the STATUS, not to the damage -- a Snow Pea still hurts.
    @Test
    void everywhereElseIceStillChills() {
        GameSession gameSession = sessionIn(EGYPT);
        Zombie zombie = spawn(gameSession);

        Element.ICE.applyOnHit(zombie.getState());

        assertTrue(zombie.getState().isChilled(),
                "outside Frostbite an ice hit must still slow a zombie");
    }

    // A zombie authored INSIDE a block is a different thing from one hit by an ice shot, and the
    // immunity must not free it -- level 2-2 opens with pre-frozen zombies in blocks.
    @Test
    void immunityDoesNotFreeAZombieAuthoredInsideABlock() {
        GameSession gameSession = sessionIn(FROSTBITE);
        Zombie zombie = spawn(gameSession);

        new FrozenTerrain().setInner("zombie", zombie, null);

        assertTrue(zombie.getState().isFrozen(),
                "an authored block holds its zombie however immune to ice attacks it is");
    }

    // ---- 2. fire melts the ice ------------------------------------------------------------------

    private static Cell blockCell(GameSession gameSession) {
        return gameSession.getMap().getRow(ROW).cellAt(BLOCK_COL);
    }

    private static boolean meltsAdjacentIce(String plantName) {
        GameSession gameSession = sessionIn(FROSTBITE);
        Plant plant = PlantFactory.createPlant(plantName, 1, PLANT_COL, ROW);
        assertNotNull(plant, plantName + " must exist in plants.json");
        gameSession.getMap().getRow(ROW).cellAt(PLANT_COL).addPlant(plant);

        FrozenTerrain block = new FrozenTerrain();
        blockCell(gameSession).addTerrain(block);

        // A warmth aura is on a one-second cadence, so a handful of ticks is not enough on its own.
        for (int tick = 0; tick < 60 && !block.isDestroyed(); tick++) {
            plant.update(gameSession);
        }
        return block.isDestroyed();
    }

    @Test
    void torchwoodMeltsTheIceBesideIt() {
        assertTrue(meltsAdjacentIce("Torchwood"),
                "Torchwood is a fire plant standing in a world made of ice and melted none of it");
    }

    @Test
    void pepperPultMeltsTheIceBesideIt() {
        assertTrue(meltsAdjacentIce("Pepper-pult"));
    }

    // The aura is fire, not a general dispel: a plant with no warmth leaves the block alone.
    @Test
    void anOrdinaryPlantMeltsNothing() {
        assertFalse(meltsAdjacentIce("Peashooter"),
                "only fire melts ice -- a Peashooter beside a block must leave it standing");
    }
}
