package models.game.gamemodes;

import models.entities.plants.Plant;
import models.entities.zombies.BossAttack;
import models.entities.zombies.Zomboss;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import models.map.Cell;
import models.map.Row;
import models.map.Terrains.FrozenTerrain;
import models.map.Terrains.NormalGrave;
import models.map.Terrains.ScorchedTerrain;
import utils.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// What each Zomboss actually DOES when its attack timer comes round.
//
// Split out of ZombossMode, which owns the fight's clock -- when to attack, when to shift rows, when
// to summon, when the machine is too dizzy to do any of it. This owns only the resolution of one
// move, and every method here is written to be safe to call at any moment: the mode never has to check
// whether there is anything to hit first.
//
// ## Everything lands through the board's own rules
//
// A fireball kills a plant by dealing it its own max HP, exactly as the tide does when it floods a
// tile out from under one; a freeze uses the same FrozenTerrain the Frostbite levels are authored
// with; a missile raises the same NormalGrave the Tomb Raiser does. Nothing here reaches past the
// model to delete an object, which is what keeps a boss's damage indistinguishable from any other
// damage as far as death handling, quests and scoring are concerned.
//
// ## Every move names its tiles
//
// Each attack reports a sentence carrying the coordinates it hit. That is not narration for its own
// sake: a boss attack resolves completely inside one call and leaves nothing on the board that says
// "this just happened here", so the sentence is the only thing the view can hang an explosion on --
// the same seam the Tomb Raiser's bones and the Dark Ages graves already use. See ToastPolicy, which
// keeps them off the screen as toasts precisely because they are drawn.
final class ZombossAttacks {

    private ZombossAttacks() { }

    // The Dark Ages fireball drops one of these where it lands.
    private static final String IMP_DRAGON = "ZombieDarkImpDragon";
    // The Sphinx raises this many headstones per missile, per the spec's "spawns graves on 2 tiles".
    private static final int MISSILE_GRAVES = 2;

    // ...but never more than this many standing at once, and that cap is not tidiness -- without it the
    // Sphinx builds itself a fortress and the fight becomes unwinnable.
    //
    // A headstone blocks projectiles (GraveTerrain.blocksProjectiles) as well as planting. Two per
    // missile, a missile every several seconds, and nothing clearing them: measured over two minutes of
    // play the board went 0 -> 2 -> 10 -> 18 blockers, by which point every pea died on a gravestone
    // and the boss took 180 damage out of 15,000. The machine was walling itself off with its own
    // attack.
    //
    // Six leaves graves a hazard worth answering -- the Egypt boss level ships Grave Buster on its belt
    // for exactly that -- while guaranteeing the majority of the lawn stays open to shoot across.
    private static final int MAX_STANDING_GRAVES = 6;
    // The Tuskmaster's gale sweeps this many rows, matching the season's own freezing wind.
    private static final int WIND_ROWS = 2;
    // A frozen zombie is dropped into every other cell of a glaciated column, so the player gets a
    // column of ice rather than a column of five zombies.
    private static final int FROZEN_SPACING = 2;

    // Runs one move. Unknown attacks are simply skipped -- a BossKind can never roll one it does not
    // own, so reaching the default at all would mean a new attack was added without a case here.
    static void perform(GameSession session, Zomboss boss, BossAttack attack, Random random) {
        if (session == null || boss == null || attack == null) {
            return;
        }
        switch (attack) {
            case FIREBALL -> fireball(session, boss, random);
            case ROW_BURN -> rowBurn(session, boss);
            case MISSILE -> missile(session, boss, random);
            case DASH -> dash(session, boss);
            case ICE_MISSILE -> iceMissile(session, boss, random);
            case ICE_WIND -> iceWind(session, boss, random);
            case FREEZE_COLUMN -> freezeColumn(session, boss, random);
            case BABY_SHARKS -> babySharks(session, boss, random);
            case TURBINE -> turbine(session, boss);
            default -> { }
        }
    }

    // ---- Dark Ages: the Zombot Dark Dragon -------------------------------------------------------

    // A fireball at a random tile: the plant burns, the ground stays too hot to replant for four
    // seconds, and an Imp Dragon drops out of the flames.
    private static void fireball(GameSession session, Zomboss boss, Random random) {
        Cell cell = randomCell(session, random);
        if (cell == null) {
            return;
        }
        int col = (int) cell.getX();
        int row = cell.getY();
        destroyPlant(cell);
        scorch(session, cell);
        Zombie imp = factories.ZombieFactory.createZombie(IMP_DRAGON, col + 0.5, row, session);
        if (imp != null) {
            session.getMap().getRow(row).getZombies().add(imp);
        }
        report(session, boss, "hurls a fireball at (" + col + ", " + row
                + ") -- the ground is left scorched and an Imp Dragon claws out of it!");
    }

    // Fire down both of its own rows: every plant standing in them is destroyed and every tile burns.
    private static void rowBurn(GameSession session, Zomboss boss) {
        for (int row : boss.occupiedRows()) {
            if (!validRow(session, row)) {
                continue;
            }
            for (Cell cell : session.getMap().getRow(row).getCells()) {
                destroyPlant(cell);
                scorch(session, cell);
            }
        }
        report(session, boss, "breathes fire down rows " + rowList(boss)
                + " -- everything in them is ash!");
    }

    // ---- Ancient Egypt: the Zombot Sphinx-inator -------------------------------------------------

    // A missile at one tile, and two headstones heaved up somewhere else while the player is looking
    // at the crater.
    private static void missile(GameSession session, Zomboss boss, Random random) {
        Cell target = randomCell(session, random);
        if (target == null) {
            return;
        }
        destroyPlant(target);
        report(session, boss, "fires a missile into (" + (int) target.getX() + ", " + target.getY()
                + ")!");
        int room = MAX_STANDING_GRAVES - standingGraves(session);
        if (room <= 0) {
            return;   // the lawn holds all the headstones it can; see MAX_STANDING_GRAVES
        }
        List<Cell> open = openCells(session);
        java.util.Collections.shuffle(open, random);
        int raising = Math.min(Math.min(MISSILE_GRAVES, room), open.size());
        for (int i = 0; i < raising; i++) {
            Cell cell = open.get(i);
            cell.addTerrain(new NormalGrave(session, cell));
            session.reportEvent("A grave heaves up out of the ground at ("
                    + (int) cell.getX() + ", " + cell.getY() + ").");
        }
    }

    // Headstones still standing anywhere on the lawn. Counted fresh each missile rather than tallied,
    // because the player breaks them and a running total would drift the moment one crumbled.
    private static int standingGraves(GameSession session) {
        int standing = 0;
        for (Row row : session.getMap().getRows()) {
            for (Cell cell : row.getCells()) {
                if (cell.hasLiveGrave()) {
                    standing++;
                }
            }
        }
        return standing;
    }

    // A charge the length of both its rows and back. Resolved as one sweep rather than as movement:
    // the machine ends where it started, so the only lasting effect is the flattening, and running it
    // over several ticks would mean the boss briefly stood in columns the rest of the fight assumes
    // it never leaves.
    private static void dash(GameSession session, Zomboss boss) {
        int flattened = 0;
        for (int row : boss.occupiedRows()) {
            if (!validRow(session, row)) {
                continue;
            }
            for (Cell cell : session.getMap().getRow(row).getCells()) {
                if (destroyPlant(cell)) {
                    flattened++;
                }
            }
        }
        report(session, boss, "charges down rows " + rowList(boss) + " and back, flattening "
                + flattened + " plants on the way!");
    }

    // ---- Frostbite Caves: the Zombot Tuskmaster --------------------------------------------------

    // An ice boulder at one tile.
    private static void iceMissile(GameSession session, Zomboss boss, Random random) {
        Cell cell = randomCell(session, random);
        if (cell == null) {
            return;
        }
        destroyPlant(cell);
        report(session, boss, "slings an ice boulder into (" + (int) cell.getX() + ", "
                + cell.getY() + ")!");
    }

    // A gale down two random rows. Chills rather than destroys -- three chills freeze a plant solid,
    // which is the season's own rule (Plant.takeIceHit), so the wind is pressure rather than a wipe.
    private static void iceWind(GameSession session, Zomboss boss, Random random) {
        int rowCount = session.getMap().getRows().size();
        List<Integer> chosen = new ArrayList<>();
        for (int i = 0; i < WIND_ROWS && rowCount > 0; i++) {
            int row = random.nextInt(rowCount);
            if (chosen.contains(row)) {
                continue;
            }
            chosen.add(row);
            for (Cell cell : session.getMap().getRow(row).getCells()) {
                if (cell.hasPlant() && !cell.getCurrentPlant().isDead()) {
                    cell.getCurrentPlant().takeIceHit();
                }
            }
        }
        for (int row : chosen) {
            report(session, boss, "blasts a wall of ice wind down row " + row + "!");
        }
    }

    // Glaciates a whole column and puts frozen zombies in it. The blocks are ordinary FrozenTerrain,
    // so a fire plant beside one melts it exactly as it melts an authored '&'.
    private static void freezeColumn(GameSession session, Zomboss boss, Random random) {
        int col = random.nextInt(Constants.BOARD_COLS);
        int rows = session.getMap().getRows().size();
        for (int row = 0; row < rows; row++) {
            Cell cell = session.getMap().getRow(row).cellAt(col);
            if (cell.hasFrozenBlock()) {
                continue;
            }
            FrozenTerrain block = new FrozenTerrain();
            cell.addTerrain(block);
            if (row % FROZEN_SPACING != 0) {
                continue;
            }
            Zombie frozen = factories.ZombieFactory.createZombie("ZombieDefault", col + 0.5, row,
                    session);
            if (frozen != null) {
                block.setInner("zombie", frozen, null);
                session.getMap().getRow(row).getZombies().add(frozen);
            }
        }
        report(session, boss, "glaciates column " + col + " -- there are zombies frozen in there!");
    }

    // ---- Big Wave Beach: the Zombot Sharktronic Sub ----------------------------------------------

    // Baby sharks up the water lanes, each swallowing one floating plant whole. Only flooded tiles:
    // a shark cannot swim up dry sand, and on a board with no water at all this simply finds nothing.
    private static void babySharks(GameSession session, Zomboss boss, Random random) {
        List<Cell> afloat = new ArrayList<>();
        for (Row row : session.getMap().getRows()) {
            for (Cell cell : row.getCells()) {
                if (cell.isFlooded() && cell.hasPlant() && !cell.getCurrentPlant().isDead()) {
                    afloat.add(cell);
                }
            }
        }
        if (afloat.isEmpty()) {
            report(session, boss, "looses a shoal of baby sharks, but there is nothing afloat to eat.");
            return;
        }
        Cell prey = afloat.get(random.nextInt(afloat.size()));
        String eaten = prey.getCurrentPlant().getName();
        destroyPlant(prey);
        report(session, boss, "sends a baby shark up the lane and it swallows the " + eaten + " at ("
                + (int) prey.getX() + ", " + prey.getY() + ") whole!");
    }

    // The turbine: everything in its two rows is dragged into the mouth and crushed. Plants AND
    // zombies, per the spec -- the sub does not care whose side the wreckage was on. The boss itself
    // is naturally exempt, since it is the thing doing the sucking.
    private static void turbine(GameSession session, Zomboss boss) {
        int crushed = 0;
        for (int row : boss.occupiedRows()) {
            if (!validRow(session, row)) {
                continue;
            }
            Row lane = session.getMap().getRow(row);
            for (Cell cell : lane.getCells()) {
                if (destroyPlant(cell)) {
                    crushed++;
                }
            }
            for (Zombie zombie : new ArrayList<>(lane.getZombies())) {
                if (zombie == boss || zombie.getHealth().isDead()) {
                    continue;
                }
                zombie.getHealth().applyDamage(zombie.getHealth().getTotalHP(),
                        models.entities.projectiles.Element.NEUTRAL, null);
                crushed++;
            }
        }
        report(session, boss, "fires up its turbine and sucks rows " + rowList(boss)
                + " into the grinder -- " + crushed + " of them are pulp!");
    }

    // ---- shared helpers --------------------------------------------------------------------------

    // Kills whatever is standing in this cell, cover included, by dealing it its own health. Returns
    // whether there was anything to kill.
    //
    // Damage rather than removal, deliberately: CombatSystem.processDeaths is what actually sweeps a
    // dead plant off its tile, and it is also what fires the plant's own death effect, tallies the
    // loss and tells the mode. A boss that unlinked the plant itself would skip all of that -- an
    // Explode-o-nut caught by a fireball would go quietly instead of taking the lane with it.
    private static boolean destroyPlant(Cell cell) {
        boolean hit = false;
        Plant cover = cell.getProtector();
        if (cover != null && !cover.isDead()) {
            cover.getHealth().takeDamage(cover.getHealth().getMaxHp());
            hit = true;
        }
        Plant plant = cell.getCurrentPlant();
        if (plant != null && !plant.isDead()) {
            plant.getHealth().takeDamage(plant.getHealth().getMaxHp());
            hit = true;
        }
        return hit;
    }

    // Leaves the tile too hot to plant on. Never stacks: a second scorch on an already-burning tile
    // would leave two terrains to expire and the tile would look burnt for twice as long as it is.
    private static void scorch(GameSession session, Cell cell) {
        boolean burning = cell.getTerrain().stream()
                .anyMatch(t -> t instanceof ScorchedTerrain && !t.isDestroyed());
        if (!burning) {
            cell.addTerrain(new ScorchedTerrain(session));
        }
    }

    // Any tile on the board, whether or not something is standing on it. A boss shooting at open
    // ground is a miss the player gets to enjoy, and a targeting rule that only ever picked occupied
    // tiles would make every single shot land.
    private static Cell randomCell(GameSession session, Random random) {
        List<Row> rows = session.getMap().getRows();
        if (rows.isEmpty()) {
            return null;
        }
        Row row = rows.get(random.nextInt(rows.size()));
        return row.cellAt(random.nextInt(Constants.BOARD_COLS));
    }

    // Bare ground a headstone could be raised on: nothing planted, nothing already on the tile.
    private static List<Cell> openCells(GameSession session) {
        List<Cell> open = new ArrayList<>();
        for (Row row : session.getMap().getRows()) {
            for (Cell cell : row.getCells()) {
                if (!cell.hasPlant() && !cell.hasProtector() && !cell.hasPlatform()
                        && !cell.isFlooded() && cell.isPlantable() && cell.getTerrain().isEmpty()) {
                    open.add(cell);
                }
            }
        }
        return open;
    }

    private static boolean validRow(GameSession session, int row) {
        return row >= 0 && row < session.getMap().getRows().size();
    }

    // "2 and 3" -- the boss's own rows, for a sentence naming them.
    private static String rowList(Zomboss boss) {
        List<Integer> rows = boss.occupiedRows();
        return rows.get(0) + " and " + rows.get(rows.size() - 1);
    }

    private static void report(GameSession session, Zomboss boss, String what) {
        session.reportEvent("The " + boss.getKind().getDisplayName() + " " + what);
    }
}
