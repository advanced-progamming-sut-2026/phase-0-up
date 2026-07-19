package models.quests.QuestCondition;

import models.quests.QuestContext;

// The completion test for a quest, evaluated against the snapshot of a finished level. Each concrete
// condition reads only the facts it cares about (sun banked, zombies killed, the final garden, ...).
public interface QuestCondition {
    boolean isSatisfied(QuestContext ctx);
}
