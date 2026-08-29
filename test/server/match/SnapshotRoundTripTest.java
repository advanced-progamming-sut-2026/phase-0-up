package server.match;

import factories.MinigameFactory;
import factories.PlantFactory;
import factories.ZombieFactory;
import models.entities.collectibles.Sun;
import models.entities.collectibles.SunType;
import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import models.game.SeedPacket;
import models.game.gamemodes.VersusIZombieMode;
import models.map.Cell;
import models.map.Row;
import models.user.Profile;
import controllers.systems.game.SunSystem;
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
        // Past the opening grace. The mode refuses every summon for the first twenty seconds so the
        // plant player can get sunflowers down, and most of what is checked below is a summoned zombie.
        serverSession.advanceTime(((VersusIZombieMode) serverSession.getMode()).graceTicks());

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

    // ---- suns, which are collected by NAMING A TILE --------------------------------------------------
    //
    // These two are about one bug with two halves. A sun is picked up by sending the tile the model
    // files it under -- floor(targetY) while it is in the air, the landed row once it is down -- so a
    // mirror that disagrees with the server about either number produces a sun the plant player can see,
    // can click, and cannot collect, for as long as it is on the board.

    @Test
    @DisplayName("a sun in mid-air is mirrored with the tile it is falling TOWARDS")
    void fallingSunKeepsItsRestingTile() throws Exception {
        // 2.6: deliberately in the lower half of lane 2, which is where this went wrong.
        Sun falling = new Sun(3.5, -1.0, 2.6, SunType.NORMAL, 25, true, 100);
        serverSession.addSun(falling);
        sync();

        Sun mirrored = mirror.getActiveSuns().get(0);
        assertTrue(mirrored.isFalling());
        assertEquals(25, mirrored.getAmount(), "hp carries the worth");
        assertEquals(falling.getX(), mirrored.getX(), 0.001);
        assertEquals(-1.0, mirrored.getCurrentY(), 0.001, "drawn where it actually is");
        // The one that mattered: built from its current height instead, this read -1 and every click
        // during the fall addressed a lane the server did not have the sun in.
        assertEquals(2.6, mirrored.getTargetY(), 0.02, "and collected by the lane it is heading for");
    }

    @Test
    @DisplayName("a landed sun is filed in the lane the server files it in")
    void landedSunSharesItsLane() throws Exception {
        Sun landed = new Sun(3.5, 2.62, 2.6, SunType.NORMAL, 25, false, 100);
        serverSession.addSun(landed);
        sync();

        // The mirror used to ROUND this and the server floors it, so a sun resting anywhere in the
        // lower half of its lane -- a bit under half of them -- was filed one lane down on the client
        // and could not be collected at all.
        assertEquals(2, landed.getY(), "the server floors");
        assertEquals(landed.getY(), mirror.getActiveSuns().get(0).getY(), "so the mirror must too");
    }

    // The whole click, end to end, which is the only assertion that could not be satisfied by two
    // wrong-but-agreeing halves.
    //
    // The plant player clicks a sun they can see on their MIRROR; the view asks that mirrored sun which
    // tile to name; the command crosses the wire as a pair of ints; the SERVER looks in that tile on its
    // own board. Every earlier assertion here compares a mirrored field to a server field, and this one
    // does the thing the player does.
    @Test
    @DisplayName("the tile the client names for a sun is the tile the server finds it in")
    void aClickOnAMirroredSunCollectsTheServersSun() throws Exception {
        SunSystem suns = new SunSystem();
        // Two sky suns with awkward resting heights: 2.6 is in the lower half of its lane, which is
        // what the mirror used to round the wrong way, and one is caught in the air on its way down.
        Sun inTheAir = new Sun(3.5, -1.4, 2.6, SunType.NORMAL, 25, true, 100);
        Sun onTheGround = new Sun(6.5, 4.55, 4.5, SunType.NORMAL, 25, false, 100);
        serverSession.addSun(inTheAir);
        serverSession.addSun(onTheGround);
        sync();

        assertEquals(2, mirror.getActiveSuns().size());
        int collected = 0;
        for (Sun mirrored : mirror.getActiveSuns()) {
            // Exactly what LawnInputProcessor.tileOf sends, asked of the sun the CLIENT holds.
            if (suns.collectSun(serverSession, mirrored.tileColumn(), mirrored.tileRow())) {
                collected++;
            }
        }
        assertEquals(2, collected, "a sun the plant player can see and click has to be collectable: "
                + "both halves of this agreed for a while and were both wrong");
        assertTrue(serverSession.getActiveSuns().isEmpty(), "and it leaves the server's board");
    }

    // ---- ability state the mirror cannot work out for itself -----------------------------------------

    @Test
    @DisplayName("a plant drawing back to shoot is announced, since a mirrored plant never runs one")
    void windUpMirrors() throws Exception {
        Plant peashooter = PlantFactory.createPlant("Peashooter", 1, 1, 1);
        serverSession.getMap().getCell(1, 1).addPlant(peashooter);
        sync();

        Plant mirrored = mirror.getMap().getCell(1, 1).getCurrentPlant();
        assertNotNull(mirrored);
        assertFalse(mirrored.isWindingUp());

        // The ability committing. Forced rather than provoked with a zombie and an engine: what is
        // under test is that the answer TRAVELS, not the shot timer that produces it.
        peashooter.mirrorWindingUp(true);
        sync();
        assertTrue(mirrored.isWindingUp(), "no ability ticks on a mirror, so without this the whole "
                + "lawn stands still while peas leave it ten times a second");

        peashooter.mirrorWindingUp(false);
        sync();
        assertFalse(mirrored.isWindingUp(), "and it has to fall again, or the clip never restarts");
    }

    @Test
    @DisplayName("a fed plant glows on both screens, and a second feed re-triggers")
    void plantFoodMirrors() throws Exception {
        Plant peashooter = PlantFactory.createPlant("Peashooter", 1, 1, 1);
        serverSession.getMap().getCell(1, 1).addPlant(peashooter);
        sync();

        Plant mirrored = mirror.getMap().getCell(1, 1).getCurrentPlant();
        assertEquals(0, mirrored.getPlantFoodFeeds());

        peashooter.mirrorPlantFood(true);
        sync();
        assertTrue(mirrored.isPlantFoodActive(), "the aura and the plantfood clip both key off this");
        assertEquals(1, mirrored.getPlantFoodFeeds());

        // A second feed. The view counts feeds rather than watching a flag, because a flag that is set
        // once can only ever announce the first one -- so the mirror has to count the rising edges.
        peashooter.mirrorPlantFood(false);
        sync();
        peashooter.mirrorPlantFood(true);
        sync();
        assertEquals(2, mirrored.getPlantFoodFeeds());
    }

    // ---- the seed bar --------------------------------------------------------------------------------

    @Test
    @DisplayName("planting darkens the card on the mirror, and it clears again when the packet is ready")
    void seedRechargeMirrors() throws Exception {
        SeedPacket onServer = serverSession.getSelectedSeed("Sunflower");
        SeedPacket onMirror = mirror.getSelectedSeed("Sunflower");
        assertNotNull(onMirror, "the plant player's HUD builds its cards from these");
        assertTrue(onMirror.isReady(0), "nothing has been planted yet");

        assertTrue(serverSession.plant(0, 0, "Sunflower").success());
        sync();

        // The recharge wipe is drawn from getRemainingCooldownSeconds, and the whole of that answer is
        // derived from lastPlantedTick -- which only GameSession.plant() writes, and which therefore
        // never moves on a board that is mirrored rather than played. Untold, every card reported
        // itself ready for the entire match and the darkness simply never appeared.
        assertFalse(onMirror.isReady(0), "the card has to go dark on the other side too");
        assertEquals(onServer.getRemainingCooldownSeconds(serverSession.getTimeTicks()),
                onMirror.getRemainingCooldownSeconds(0), 0.2,
                "and by the same amount, or the wipe drains at the wrong speed");

        // Run the packet out. The mirror is told a remaining time every tick, including zero -- a
        // packet only told while it is cooling would never hear that it had finished.
        serverSession.advanceTime(onServer.getCooldownDuration() * Constants.TICKS_PER_SECOND);
        sync();
        assertTrue(onMirror.isReady(0), "and clear again when the server says it is ready");
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
