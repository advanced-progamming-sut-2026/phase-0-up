package models.quests;

import models.quests.QuestCondition.QuestCondition;
import models.quests.Reward.Reward;

public abstract class Quest {
    private String name;
    QuestPriority priority;
    Reward reward;
    QuestCondition condition;


    public abstract boolean isComplete(QuestContext ctx);

}
