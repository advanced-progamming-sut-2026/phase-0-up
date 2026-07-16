package models.game.gamemodes;

import java.util.ArrayList;
import java.util.List;

// Special level: some plants are banned from selection. The authoritative restriction is the level's
// availablePlants list (already enforced by the seed-selection menu); this mode carries the banned
// set for display and a defensive isPlantAllowed check. lockedType distinguishes the doc's two
// variants (1 = a family is locked down, 2 = the player is forced into a fixed loadout).
public class LockedPlantsMode extends StandardMode {
    private final int type;
    private final List<String> bannedPlants;

    public LockedPlantsMode(int type, List<String> bannedPlants) {
        this.type = type;
        this.bannedPlants = bannedPlants != null ? bannedPlants : new ArrayList<>();
    }

    public int getType() {
        return type;
    }

    public List<String> getBannedPlants() {
        return bannedPlants;
    }

    public boolean isPlantAllowed(String plantName) {
        return !bannedPlants.contains(plantName);
    }
}
