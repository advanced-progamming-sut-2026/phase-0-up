package server.match;

import factories.MinigameFactory;
import factories.PlantFactory;
import factories.ZombieFactory;
import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import models.game.gamemodes.VersusIZombieMode;
import models.map.Cell;
import models.map.Row;
import models.user.Profile;
import net.PacketCodec;
import net.packets.MatchSnapshot;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import utils.Constants;
import utils.gameinitializers.GameInitializer;
import views.gdx.bridge.SnapshotReconciler;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The full loop: an authoritative board -> a snapshot -> JSON -> a client's mirror -> the same board.
//
// This is the test the whole networked match rests on, and it is deliberately a ROUND TRIP through the
// codec rather than a direct hand-off. A snapshot that is built perfectly and serialised wrongly looks
// identical from inside the server, and the failure -- entities in the wrong lane, or missing -- shows
// up on a screen nobody is running in CI.
//
// No socket and no GL context: the reconciler writes plain model objects, so both halves of a
// networked match can be checked in milliseconds.
class SnapshotRoundTripTest {

    @BeforeAll
    static void loadGameData() {
        new GameInitializer().loadAllData();
    }

    private final PacketCodec codec = new PacketCodec();
    private final SnapshotBuilder builder = new SnapshotBuilder();

    private GameSession serverSession;
    private GameSession mirror;
    private SnapshotReconciler reconciler;

    @BeforeEach
    void buildBoards() {
        serverSession = new GameSession(new Profile(), MinigameFactory.createVersusIZombie());
        serverSession.startMode();

        // The client builds the same level and never starts the mode: everything onStart would do has
        // already been done on the server, and doing it again locally is a second opinion about a
        // board this machine does not own.
        mirror = new GameSession(new Profile(), MinigameFactory.createVersusIZombie());
        reconciler = new SnapshotReconciler(mirror);
        reconciler.clearMowers();
    }

    // Builds a snapshot of the server board, puts it through the wire format, and applies it.
    private MatchSnapshot sync() throws Exception {
        MatchSnapshot built = builder.build(serverSession);
        MatchSnapshot decoded = codec.decode(codec.encode(built)).as(MatchSnapshot.class);
        reconciler.apply(decoded);
        return decoded;
    }

    // ---- the opening board -----------------------------------------------------------------------

    @Test
    @DisplayName("the opening board arrives whole: five sun makers, five brains, both banks")
    void openingBoardMirrors() throws Exception {
        MatchSnapshot snapshot = sync();

        assertEquals(zombies(serverSession).size(), zombies(mirror).size());
        assertEquals(Constants.BOARD_ROWS, zombies(mirror).size(), "one sun maker per lane");
        assertEquals(serverSession.getSunAmount(), mirror.getSunAmount());
        assertEquals(mode(serverSession).getZombieSun(), mode(mirror).getZombieSun());
        assertEquals(mode(serverSession).brainsTotal(), snapshot.brainEaten().length);

        // The mirror's GameMap builds a mower in every row and a brain lawn has none. Without
        // clearMowers the client would draw five mowers under five brains.
        for (Row row : mirror.getMap().getRows()) {
            assertNull(row.getLawnmower());
        }
    }

    @Test
    @DisplayName("the sun makers are recognisable as such on the mirror")
    void sunProducersSurvive() throws Exception {
        sync();
        int flagged = 0;
        for (Zombie zombie : zombies(mirror)) {
            if (mode(mirror).isSunProducer(zombie)) {
                flagged++;
            }
        }
        // The mode that designated them runs on the server. Without the flag the client draws five
        // ordinary bucketheads standing suspiciously still at column 8.
        assertEquals(Constants.BOARD_ROWS, flagged);
    }

    // ---- entities --------------------------------------------------------------------------------

    @Test
    @DisplayName("a summoned zombie appears in the right lane at the right place")
    void zombiesMirror() throws Exception {
        assertTrue(serverSession.summonZombie("ZombieImp", 8, 3).success());
        sync();

        List<Zombie> lane = mirror.getMap().getRow(3).getZombies();
        Zombie imp = lane.stream()
                .filter(zombie -> "ZombieImp".equalsIgnoreCase(zombie.getAlias()))
                .findFirst().orElse(null);
        assertNotNull(imp, "the summon has to reach the other player's screen");
        assertEquals(8, Math.round(imp.getMovement().getPositionX()));
        assertEquals(3, imp.getMovement().getPositionY());
    }

    @Test
    @DisplayName("a zombie walking is followed rather than re-created every tick")
    void movementIsFollowed() throws Exception {
        assertTrue(serverSession.summonZombie("ZombieImp", 8, 1).success());
        sync();
        Zombie mirrored = mirror.getMap().getRow(1).getZombies().stream()
                .filter(zombie -> "ZombieImp".equalsIgnoreCase(zombie.getAlias()))
                .findFirst().orElseThrow();

        serverZombie("ZombieImp").getMovement().setPositionX(5.5);
        sync();

        // The SAME object, moved. A mirror that destroyed and rebuilt it would lose the
        // EntityInterpolator's track and the zombie would teleport ten times a second.
        assertEquals(5.5, mirrored.getMovement().getPositionX(), 0.01);
        assertEquals(1, countOf(mirror, "ZombieImp"), "and there must be exactly one of it");
    }

    @Test
    @DisplayName("a lane change is re-filed in both places the lane is kept")
    void laneChangesAreRefiled() throws Exception {
        assertTrue(serverSession.summonZombie("ZombieImp", 8, 0).success());
        sync();

        Zombie onServer = serverZombie("ZombieImp");
        serverSession.getMap().getRow(0).getZombies().remove(onServer);
        onServer.getMovement().setPositionY(4);
        serverSession.getMap().getRow(4).getZombies().add(onServer);
        sync();

        // The lane lives in the movement component AND in the Row's list, and only both together are
        // the zombie's position -- this project has already shipped that bug once.
        assertEquals(0, countOf(mirror.getMap().getRow(0).getZombies(), "ZombieImp"));
        assertEquals(1, countOf(mirror.getMap().getRow(4).getZombies(), "ZombieImp"));
        assertEquals(4, mirror.getMap().getRow(4).getZombies().stream()
                .filter(z -> "ZombieImp".equalsIgnoreCase(z.getAlias()))
                .findFirst().orElseThrow().getMovement().getPositionY());
    }

    @Test
    @DisplayName("a plant appears on the mirror and disappears when it is dug up")
    void plantsMirror() throws Exception {
        Plant plant = PlantFactory.createPlant("Sunflower", 1, 2, 2);
        serverSession.getMap().getCell(2, 2).addPlant(plant);
        sync();

        // The exact tile, and it is the assertion that matters. A plant sits at `column + 0.5` -- the
        // middle of its tile -- so a reconciler that ROUNDS x instead of flooring it puts every plant
        // one column to the right, on a board that otherwise looks completely normal.
        Cell mirrored = mirror.getMap().getCell(2, 2);
        assertNotNull(mirrored.getCurrentPlant(), "column 2, not column 3");
        assertEquals("Sunflower", mirrored.getCurrentPlant().getName());
        assertEquals(plant.getX(), mirrored.getCurrentPlant().getX(), 0.001);
        assertNull(mirror.getMap().getCell(3, 2).getCurrentPlant());

        serverSession.getMap().getCell(2, 2).removePlant();
        sync();
        assertNull(mirror.getMap().getCell(2, 2).getCurrentPlant(),
                "an entity that stops appearing in snapshots has to leave the mirror too");
    }

    @Test
    @DisplayName("damage is mirrored, and it peels armour on the way")
    void damageMirrors() throws Exception {
        assertTrue(serverSession.summonZombie("ZombieDefault", 8, 2).success());
        sync();

        Zombie onServer = serverZombie("ZombieDefault");
        Zombie mirrored = mirror.getMap().getRow(2).getZombies().stream()
                .filter(z -> "ZombieDefault".equalsIgnoreCase(z.getAlias()))
                .findFirst().orElseThrow();

        int before = mirrored.getHealth().getTotalHP();
        onServer.getHealth().applyDamage(50, null, null);
        sync();

        assertEquals(onServer.getHealth().getTotalHP(), mirrored.getHealth().getTotalHP());
        assertTrue(mirrored.getHealth().getTotalHP() < before);
    }

    // ---- the numbers above the board ---------------------------------------------------------------

    @Test
    @DisplayName("both banks and the clock are told to the mirror, not derived by it")
    void banksAndClockMirror() throws Exception {
        serverSession.increaseSunAmount(123);
        serverSession.advanceTime(40);
        MatchSnapshot snapshot = sync();

        assertEquals(serverSession.getSunAmount(), mirror.getSunAmount());
        assertEquals(mode(serverSession).getZombieSun(), mode(mirror).getZombieSun());
        // The mirror's session never ticks, so a clock it computed itself would read full for the
        // whole match. It has to come off the snapshot.
        assertEquals(snapshot.ticksRemaining(), mode(mirror).ticksRemaining(mirror));
        assertTrue(mode(mirror).isMirrored());
        assertFalse(mode(serverSession).isMirrored());
    }

    @Test
    @DisplayName("an eaten brain shows as eaten on both screens")
    void brainsMirror() throws Exception {
        // A zombie at the house eats that lane's brain on the mode's next tick.
        serverSession.getMap().getRow(2).getZombies()
                .add(ZombieFactory.createZombie("ZombieDefault", 0, 2, serverSession));
        serverSession.evaluateModeRules();
        sync();

        assertTrue(mode(mirror).isBrainEaten(2));
        assertFalse(mode(mirror).isBrainEaten(0));
        assertEquals(mode(serverSession).brainsEaten(), mode(mirror).brainsEaten());
    }

    // ---- the mirror does not drift ------------------------------------------------------------------

    @Test
    @DisplayName("twenty ticks of real play leave the two boards identical")
    void boardsStayInStepOverTime() throws Exception {
        assertTrue(serverSession.summonZombie("ZombieImp", 8, 0).success());
        assertTrue(serverSession.summonZombie("ZombieDefault", 7, 4).success());
        Plant plant = PlantFactory.createPlant("Peashooter", 1, 1, 0);
        serverSession.getMap().getCell(1, 0).addPlant(plant);

        for (int tick = 0; tick < 20; tick++) {
            serverSession.advanceTime(1);
            serverSession.evaluateModeRules();
            sync();
        }

        // Counting, not sampling: a reconciler that leaks builds up a second copy of everything, and a
        // reconciler that double-removes quietly empties the lawn. Neither shows in one entity's
        // coordinates.
        assertEquals(zombies(serverSession).size(), zombies(mirror).size());
        assertEquals(plants(serverSession).size(), plants(mirror).size());
        for (int lane = 0; lane < Constants.BOARD_ROWS; lane++) {
            assertEquals(serverSession.getMap().getRow(lane).getZombies().size(),
                    mirror.getMap().getRow(lane).getZombies().size(),
                    "lane " + lane + " drifted");
        }
    }

    @Test
    @DisplayName("replaying an older snapshot does not rubber-band the board")
    void staleSnapshotsAreIgnored() throws Exception {
        assertTrue(serverSession.summonZombie("ZombieImp", 8, 0).success());
        MatchSnapshot old = builder.build(serverSession);
        reconciler.apply(old);

        serverSession.advanceTime(5);
        serverZombie("ZombieImp").getMovement().setPositionX(3.0);
        sync();

        reconciler.apply(old);   // arrives late, or is replayed by a caller

        assertEquals(3.0, mirror.getMap().getRow(0).getZombies().stream()
                .filter(z -> "ZombieImp".equalsIgnoreCase(z.getAlias()))
                .findFirst().orElseThrow().getMovement().getPositionX(), 0.01);
    }

    // ---- helpers -----------------------------------------------------------------------------------

    private static VersusIZombieMode mode(GameSession session) {
        return (VersusIZombieMode) session.getMode();
    }

    private static List<Zombie> zombies(GameSession session) {
        List<Zombie> all = new ArrayList<>();
        for (Row row : session.getMap().getRows()) {
            all.addAll(row.getZombies());
        }
        return all;
    }

    private static List<Plant> plants(GameSession session) {
        List<Plant> all = new ArrayList<>();
        for (Row row : session.getMap().getRows()) {
            for (Cell cell : row.getCells()) {
                if (cell.getCurrentPlant() != null) {
                    all.add(cell.getCurrentPlant());
                }
            }
        }
        return all;
    }

    private Zombie serverZombie(String alias) {
        return zombies(serverSession).stream()
                .filter(zombie -> alias.equalsIgnoreCase(zombie.getAlias()))
                .filter(zombie -> !mode(serverSession).isSunProducer(zombie))
                .findFirst().orElseThrow();
    }

    private static int countOf(GameSession session, String alias) {
        return countOf(zombies(session), alias);
    }

    private static int countOf(List<Zombie> zombies, String alias) {
        int n = 0;
        for (Zombie zombie : zombies) {
            if (alias.equalsIgnoreCase(zombie.getAlias())) {
                n++;
            }
        }
        return n;
    }
}
