package factories;

import models.game.Level;
import models.game.Wave;
import models.game.gamemodes.BeghouledMode;
import models.game.gamemodes.IZombieMode;
import models.game.gamemodes.VaseBreakerMode;
import models.game.gamemodes.WallnutBowlingMode;

import java.util.ArrayList;

// Builds a self-contained Level for a mini-game launched from the Travel Log. Mini-games are not part
// of the adventure map, so they carry no LevelTemplate, no waves, and no selectable-plant pool -- the
// mode generates everything it needs in onStart. The board is the standard 5x9 grid the GameMap always
// builds, so a null terrain layout is fine.
public final class MinigameFactory {
    private MinigameFactory() { }

    public static Level createVasebreaker(int difficulty) {
        VaseBreakerMode mode = new VaseBreakerMode(Math.max(1, difficulty));
        return new Level(new Wave[0], null, mode, 0, new ArrayList<>(), 0, 0, null);
    }

    public static Level createWallnutBowling(int difficulty) {
        WallnutBowlingMode mode = new WallnutBowlingMode(Math.max(1, difficulty));
        return new Level(new Wave[0], null, mode, 0, new ArrayList<>(), 0, 0, null);
    }

    public static Level createIZombie(int difficulty) {
        IZombieMode mode = new IZombieMode(Math.max(1, difficulty));
        // The mode itself sets the 150-sun starting bank in onStart.
        return new Level(new Wave[0], null, mode, 0, new ArrayList<>(), 0, 0, null);
    }

    public static Level createBeghouled(int difficulty) {
        BeghouledMode mode = new BeghouledMode(Math.max(1, difficulty));
        return new Level(new Wave[0], null, mode, 0, new ArrayList<>(), 0, 0, null);
    }
}
