package models.greenhouse;


import java.util.ArrayList;
import java.util.List;

public class GreenHouse {
    private Pot[][] pots;
    private final int rows = 4;
    private final int cols = 5;

    public GreenHouse() {
        this.pots = new Pot[rows][cols];
        initializePots();
    }

    private void initializePots() {
        for (int i = 0; i < rows; i++){
            for (int j = 0; j < cols; j++){
                pots[i][j] = new Pot(j, i);
                if (i == 0){
                    Pot pot = pots[i][j];
                    pot.setState(PotState.EMPTY);
                }
            }
        }
    }

    public List<Pot> getUnlockedPots() {
        List<Pot> unlockedPots = new ArrayList<>();
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                if (!pots[i][j].isLocked()) {
                    unlockedPots.add(pots[i][j]);
                }
            }
        }
        return unlockedPots;
    }

    public Pot getPot(int x, int y) {
        return pots[y][x];
    }

    // Opens the next locked pot in reading order (left to right, top to bottom) and reports whether
    // there was one left to open. Row 0 ships unlocked, so this is how every later pot is earned --
    // bought from the shop, or dropped by a zombie.
    public Pot unlockNextPot() {
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                if (pots[i][j].isLocked()) {
                    pots[i][j].setState(PotState.EMPTY);
                    return pots[i][j];
                }
            }
        }
        return null;
    }

    public boolean isFull() {
        return getUnlockedPots().size() >= rows * cols;
    }

    public void plantPot(int x, int y, GreenHousePlant plant){
        Pot pot = pots[y][x];
        pot.setOnPot(plant);
        pot.setState(PotState.GROWING);
        pot.setReadyAtTimestamp(System.currentTimeMillis() + plant.getGrowthDuration());
    }

    public GreenHousePlant collect(int x, int y){
        Pot pot =  pots[y][x];

        GreenHousePlant harvestedPlant = pot.getOnPot();

        pot.setState(PotState.EMPTY);
        pot.setOnPot(null);
        pot.setReadyAtTimestamp(0);

        return harvestedPlant;
    }

    public int getGrowthCostInDiamonds(int x, int y){
        return pots[y][x].getRemainingHoursCeil();
    }

    public void growPlantWithDiamonds(int x, int y){
        pots[y][x].instantGrow();
    }

    public boolean isValidCoordinate(int userX, int userY) {
        int x = userX - 1;
        int y = userY - 1;
        return x >= 0 && x < cols && y >= 0 && y < rows;
    }

    public int getRows() {
        return rows;
    }

    public int getCols() {
        return cols;
    }

}
