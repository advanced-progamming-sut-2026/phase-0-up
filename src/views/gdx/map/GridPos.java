package views.gdx.map;

import utils.Constants;

// A lawn tile, in the model's own terms: col 0 is nearest the house, row 0 is the top lane.
//
// Deliberately the same (column, row) order the CLI uses -- "plant plant -t Sunflower -l (0, 2)" is
// column 0, row 2 -- so a click can be turned into a command string without anyone having to remember
// which way round it goes.
public record GridPos(int col, int row) {

    public boolean isValid() {
        return col >= 0 && col < Constants.BOARD_COLS && row >= 0 && row < Constants.BOARD_ROWS;
    }

    // Formatted the way every in-game location command expects it: "(3, 2)".
    public String toCommandArgs() {
        return "(" + col + ", " + row + ")";
    }

    @Override
    public String toString() {
        return "col=" + col + ",row=" + row;
    }
}
