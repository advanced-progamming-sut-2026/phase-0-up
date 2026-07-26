package models.minigames;

import models.map.Cell;

// Model-side sketch of the Beghouled match-3. The played implementation is
// models.game.gamemodes.BeghouledMode, which owns the board, the swap/upgrade rules and the match
// tally. The targetMatches field here was never read, so it was removed for PMD's UnusedPrivateField
// rule; BeghouledMode holds the real match target.
public class Beghouled {
    public void swap(Cell a, Cell b){}
    public void upgrade(Upgrade upgrade){}
    public int resolveMatches(){return 0;}
}
