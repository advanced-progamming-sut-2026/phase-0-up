package models.quests.Reward;

import models.templates.PlantTemplate;
import models.user.Profile;
import utils.registry.PlantRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// Unlockable reward: flips a plant or a level from locked to available on the profile. A "random"
// plant target picks any plant the player has not unlocked yet.
public class UnlockableReward extends Reward {
    public enum Target { PLANT, LEVEL }

    public static final String RANDOM = "random";

    private final Target target;
    private final String item;     // a plant name, "random", or (for LEVEL) unused
    private final Random random = new Random();

    public UnlockableReward(Target target, String item) {
        this.target = target;
        this.item = item;
    }

    @Override
    public void grant(Profile profile) {
        if (profile == null) {
            return;
        }
        if (target == Target.LEVEL) {
            profile.setLastLevel(profile.getLastLevel() + 1);   // open the next level
            controllers.systems.NewsSystem.getInstance().addLevelUnlockNews(profile, "a new level");
            return;
        }
        String plant = RANDOM.equalsIgnoreCase(item) ? randomLockedPlant(profile) : item;
        if (plant != null && profile.unlockPlant(plant)) {
            controllers.systems.NewsSystem.getInstance().addPlantUnlockNews(profile, plant);
        }
    }

    // Any catalogue plant the player has not unlocked yet, or null if they already own them all.
    private String randomLockedPlant(Profile profile) {
        List<String> locked = new ArrayList<>();
        for (PlantTemplate t : PlantRegistry.getInstance().getAllPlantTemplates().values()) {
            if (!profile.getUnlockedPlants().contains(t.getName().toLowerCase().trim())) {
                locked.add(t.getName());
            }
        }
        return locked.isEmpty() ? null : locked.get(random.nextInt(locked.size()));
    }

    @Override
    public String describe() {
        if (target == Target.LEVEL) {
            return "a new level";
        }
        return RANDOM.equalsIgnoreCase(item) ? "a random new plant" : ("the " + item + " plant");
    }

    public Target getTarget() { return target; }
    public String getItem() { return item; }
}
