package models.quests.Reward;

import models.templates.PlantTemplate;
import models.user.Profile;
import utils.registry.PlantRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// Inventory reward: stocks consumable items in the player's collection. Today the only item is the
// seed packet (used to upgrade plants). A batch of packets is spread across random unlocked plants,
// the same way a random seed-pack purchase behaves.
public class InventoryReward extends Reward {
    public static final String SEED_PACKET = "SEED_PACKET";

    private final String item;
    private final int amount;
    private final Random random = new Random();

    public InventoryReward(String item, int amount) {
        this.item = item == null ? SEED_PACKET : item;
        this.amount = amount;
    }

    @Override
    public void grant(Profile profile) {
        if (profile == null || amount <= 0 || !SEED_PACKET.equalsIgnoreCase(item)) {
            return;
        }
        List<String> pool = new ArrayList<>(profile.getUnlockedPlants());
        if (pool.isEmpty()) {
            // Nothing unlocked yet -> fall back to the full plant catalogue so the packets aren't lost.
            for (PlantTemplate t : PlantRegistry.getInstance().getAllPlantTemplates().values()) {
                pool.add(t.getName());
            }
        }
        if (pool.isEmpty()) {
            return;
        }
        for (int i = 0; i < amount; i++) {
            profile.addSeedPackets(pool.get(random.nextInt(pool.size())), 1);
        }
    }

    @Override
    public String describe() {
        return amount + " seed packet" + (amount == 1 ? "" : "s");
    }

    public String getItem() { return item; }
    public int getAmount() { return amount; }
}
