package models.quests.QuestCondition;

import models.quests.QuestContext;

// Placeholder-free null object for a quest whose completion needs bespoke gameplay tracking not yet
// wired (e.g. "kill only with the Cactus", "win using only night plants"). It never auto-completes,
// so such a quest simply stays open rather than completing on the wrong signal.
public class NoCondition implements QuestCondition {
    @Override
    public boolean isSatisfied(QuestContext ctx) {
        return false;
    }
}
