package models.game.gamemodes;

import models.game.GameSession;
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
        return !isSeedForced(plantType);
    }
    // Ignoring case and space: the forced list carries the level's casing, the player types their own.
    @Override
    public boolean isSeedForced(String plantType) {
        if (plantType == null) {
            return false;
        }
        String wanted = plantType.trim();
        for (String forced : forcedPlants) {
            if (forced != null && forced.trim().equalsIgnoreCase(wanted)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<String> preSelectedPlants() {
        return forcedPlants;
    }
    // Announced at level open, so the pre-filled bar and missing slots are not a surprise.
    @Override
    public void onStart(GameSession session) {
        super.onStart(session);
        session.reportEvent(startBanner(session));
    }
    private String startBanner(GameSession session) {
        StringBuilder sb = new StringBuilder("Locked Plants! The lawn picked your loadout for you.");
        if (!forcedPlants.isEmpty()) {
            sb.append(" Bolted to the seed bar: ").append(joinNames()).append(" -- these cannot be")
                    .append(" swapped out.");
        }
        if (lockedSlots > 0) {
            sb.append(" ").append(lockedSlots).append(lockedSlots == 1 ? " seed slot is" : " seed slots are")
                    .append(" welded shut, leaving you ").append(session.getMaxSeedSlots()).append(".");
        }
        return sb.toString();
    }
    private String joinNames() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < forcedPlants.size(); i++) {
            if (i > 0) {
                sb.append(i == forcedPlants.size() - 1 ? " and " : ", ");
            }
            sb.append(forcedPlants.get(i));
        }
        return sb.toString();
    }
}
