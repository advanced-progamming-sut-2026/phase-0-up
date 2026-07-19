package models.quests;

import models.quests.Reward.Reward;

// An epic challenge -- hard, gem-rewarding quests that fund the special abilities in the shop.
public class EpicQuest extends Quest {
    public EpicQuest(String id, String name, String description, QuestPriority priority,
                     Reward reward, String variables) {
        super(id, name, description, Category.EPIC, priority, reward, variables);
    }
}
