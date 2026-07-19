package models.templates;

// Blueprint for one quest, parsed straight from data/quests.json by Gson. The reward is a nested
// object so the polymorphic reward strategy can be built from it without the parser knowing the
// concrete reward type.
public class QuestTemplate {
    private String id;
    private String name;
    private String description;
    private String category;    // DAILY | MAIN | EPIC
    private String priority;    // CRITICAL | HIGH | MEDIUM | LOW
    private RewardSpec reward;
    private ConditionSpec condition;
    private String variables;   // free-text note on the quest's tunable variables

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public String getPriority() { return priority; }
    public RewardSpec getReward() { return reward; }
    public ConditionSpec getCondition() { return condition; }
    public String getVariables() { return variables; }

    // The quest's completion test. `type` names the condition (COLLECT_SUN, ZERO_SUN, KILL_COUNT,
    // MAX_PLANTS_LOST, LAWNMOWER_KILLS, EMPTY_ROW, EMPTY_COLUMN, SYMMETRIC, ASYMMETRIC); `threshold`
    // and `index` carry its parameter. Absent -> the quest has no auto-completion yet.
    public static class ConditionSpec {
        private String type;
        private int threshold;
        private int index;
        private String plant;      // for KILL_WITH_PLANT: the plant that must get all the kills
        private String category;   // for PLANT_CATEGORY_COUNT / ONLY_CATEGORY: the plant category
        private String family;     // for WITHOUT_FAMILY: the plant family (category) that must go unused

        public String getType() { return type; }
        public int getThreshold() { return threshold; }
        public int getIndex() { return index; }
        public String getPlant() { return plant; }
        public String getCategory() { return category; }
        public String getFamily() { return family; }
    }

    // The reward payload. Which fields are meaningful depends on `category`:
    //   CURRENCY   -> currency (COINS|GEMS) + amount
    //   INVENTORY  -> item (SEED_PACKET) + amount
    //   UNLOCKABLE -> target (PLANT|LEVEL) + item (a plant name or "random")
    public static class RewardSpec {
        private String category;
        private String currency;
        private String item;
        private String target;
        private int amount;

        public String getCategory() { return category; }
        public String getCurrency() { return currency; }
        public String getItem() { return item; }
        public String getTarget() { return target; }
        public int getAmount() { return amount; }
    }
}
