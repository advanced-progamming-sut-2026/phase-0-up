package models.quests;

import models.quests.Reward.Reward;

// A story / progression quest -- unlocking plants the player's advancement depends on. These carry
// the highest weight in the travel log.
public class MainQuest extends Quest {
    public MainQuest(String id, String name, String description, QuestPriority priority,
                     Reward reward, String variables) {
        super(id, name, description, Category.MAIN, priority, reward, variables);
    }
}
