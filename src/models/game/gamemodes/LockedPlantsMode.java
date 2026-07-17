package models.game.gamemodes;

import java.util.ArrayList;
import java.util.List;

// Special level: the loadout is constrained beyond the level's plant pool.
// Which plants may be picked is already the level's availablePlants list, so this mode only carries
// what a pool cannot express -- the two variants from the doc:
//   type 1 -> lockedSlots: seed slots are shut from the start, so fewer plants fit in the loadout.
//   type 2 -> forcedPlants: seeds are pre-selected and the player may not remove them.
public class LockedPlantsMode extends StandardMode {
    private final int type;
    private final int lockedSlots;
    private final List<String> forcedPlants;

    public LockedPlantsMode(int type, int lockedSlots, List<String> forcedPlants) {
        this.type = type;
        this.lockedSlots = Math.max(0, lockedSlots);
        this.forcedPlants = forcedPlants != null ? forcedPlants : new ArrayList<>();
    }

    public int getType() {
        return type;
    }

    public int getLockedSlots() {
        return lockedSlots;
    }

    public List<String> getForcedPlants() {
        return forcedPlants;
    }

    @Override
    public int adjustSeedSlots(int baseSlots) {
        // Never lock every slot away, or the level becomes unwinnable.
        return Math.max(1, baseSlots - lockedSlots);
    }

    @Override
    public boolean isSeedRemovable(String plantType) {
        return !forcedPlants.contains(plantType);
    }

    @Override
    public List<String> preSelectedPlants() {
        return forcedPlants;
    }
}
