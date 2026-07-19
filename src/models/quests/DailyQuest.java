package models.quests;

import models.quests.Reward.Reward;

// A daily, repeatable quest -- the "log in every day" tier. Medium/low priority by nature.
public class DailyQuest extends Quest {
    public DailyQuest(String id, String name, String description, QuestPriority priority,
                      Reward reward, String variables) {
        super(id, name, description, Category.DAILY, priority, reward, variables);
    }
}
