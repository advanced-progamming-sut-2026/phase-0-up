package models.greenhouse;


import java.util.ArrayList;
import java.util.List;

// The player's greenhouse: a fixed grid of pots, each growing at most one plant.
//
// **Three rows of four, twelve pots.** Phase 1 had four rows of five, and the count is not arbitrary:
// the shipped greenhouse background (IMAGE_BACKGROUNDS_ZEN_GARDEN) is painted with exactly twelve slat
// mats in a 3x4 arrangement, and the spec asks for "a pot placed on each slot". Twenty pots could only
// ever be drawn by inventing shelves the art does not have.
//
// **Coordinates are 0-based throughout this class.** getPot(x, y) is pots[y][x] -- x is the COLUMN, y is
// the ROW -- and isValidCoordinate takes the same 0-based pair. It used to take a 1-based one, which
// meant every caller held two conventions at once and had to remember which method wanted which. The
// player still types 1-based coordinates ("plant pot at (1, 1)" is the top-left pot, as the spec
// documents); the three greenhouse Commands do that one conversion, and nothing else in the codebase
// has to think about it.
public class GreenHouse {
    private Pot[][] pots;
    private final int rows = 3;
    private final int cols = 4;

    public GreenHouse() {
        this.pots = freshGrid(null);
    }

    // Every read of the grid goes through here, and that is what makes the Phase 1 -> Phase 2 migration
    // safe.
    //
    // Gson deserialises straight past the constructor and writes whatever the save file holds -- so a
    // profile saved in Phase 1 arrives with a 4x5 array while this class believes it has a 3x4 one.
    // Nothing would throw: reads inside the smaller bounds would work, the eight surplus pots would
    // simply become unreachable, and isFull() would disagree with the array it is counting.
    //
    // Reshaping lazily on first touch rather than in a repair method called from the six places that
    // load a Profile (LoginCommand, Main, PvZGame twice, CampaignSystem, DevBoot) means no load path can
    // forget it -- including any added later. The cost is one length check per access.
    private Pot[][] grid() {
        if (pots == null || pots.length != rows || pots[0] == null || pots[0].length != cols) {
            pots = freshGrid(pots);
        }
        return pots;
    }

    // Builds the 3x4 grid, carrying over as much of a legacy grid as fits.
    //
    // Pots carry over in READING ORDER, which is also the order unlockNextPot opens them in -- so the
    // twelve that survive are exactly the earliest twelve, and a player's unlocked count is preserved up
    // to the new ceiling. A pot's growing plant and its ready-at timestamp come with it; only its
    // coordinates are rewritten, since it may land in a different column.
    private Pot[][] freshGrid(Pot[][] legacy) {
        List<Pot> carried = inReadingOrder(legacy);
        Pot[][] grid = new Pot[rows][cols];
        int next = 0;
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                Pot pot = next < carried.size() ? carried.get(next++) : null;
                if (pot == null) {
                    pot = new Pot(x, y);
                    // Row 0 ships unlocked, exactly as it always has. Every later pot is earned -- bought
                    // from the shop, or dropped by a zombie.
                    pot.setState(y == 0 ? PotState.EMPTY : PotState.LOCKED);
                } else {
                    pot.setX(x);
                    pot.setY(y);
                }
                grid[y][x] = pot;
            }
        }
        return grid;
    }

    private static List<Pot> inReadingOrder(Pot[][] legacy) {
        List<Pot> flat = new ArrayList<>();
        if (legacy == null) {
            return flat;
        }
        for (Pot[] row : legacy) {
            if (row == null) {
                continue;
            }
            for (Pot pot : row) {
                if (pot != null) {
                    flat.add(pot);
                }
            }
        }
        return flat;
    }

    public List<Pot> getUnlockedPots() {
        List<Pot> unlockedPots = new ArrayList<>();
        for (Pot[] row : grid()) {
            for (Pot pot : row) {
                if (!pot.isLocked()) {
                    unlockedPots.add(pot);
                }
            }
        }
        return unlockedPots;
    }

    // 0-based, and transposed: x is the column, y is the row.
    public Pot getPot(int x, int y) {
        return grid()[y][x];
    }

    // Opens the next locked pot in reading order (left to right, top to bottom) and reports whether
    // there was one left to open. Row 0 ships unlocked, so this is how every later pot is earned --
    // bought from the shop, or dropped by a zombie.
    public Pot unlockNextPot() {
        for (Pot[] row : grid()) {
            for (Pot pot : row) {
                if (pot.isLocked()) {
                    pot.setState(PotState.EMPTY);
                    return pot;
                }
            }
        }
        return null;
    }

    public boolean isFull() {
        return getUnlockedPots().size() >= rows * cols;
    }

    public void plantPot(int x, int y, GreenHousePlant plant){
        Pot pot = getPot(x, y);
        pot.setOnPot(plant);
        pot.setState(PotState.GROWING);
        pot.setReadyAtTimestamp(System.currentTimeMillis() + plant.getGrowthDuration());
    }

    public GreenHousePlant collect(int x, int y){
        Pot pot = getPot(x, y);

        GreenHousePlant harvestedPlant = pot.getOnPot();

        pot.setState(PotState.EMPTY);
        pot.setOnPot(null);
        pot.setReadyAtTimestamp(0);

        return harvestedPlant;
    }

    public int getGrowthCostInGems(int x, int y){
        return getPot(x, y).getRemainingHoursCeil();
    }

    public void growPlantWithGems(int x, int y){
        getPot(x, y).instantGrow();
    }

    // 0-based, matching getPot. Callers holding a coordinate the player typed convert it first.
    public boolean isValidCoordinate(int x, int y) {
        return x >= 0 && x < cols && y >= 0 && y < rows;
    }

    public int getRows() {
        return rows;
    }

    public int getCols() {
        return cols;
    }

}
