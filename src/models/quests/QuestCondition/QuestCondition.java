package models.quests.QuestCondition;

import models.quests.QuestContext;
import models.quests.QuestProgress;
import models.user.Profile;

// The completion test for a quest, evaluated against the snapshot of a finished level. Each concrete
// condition reads only the facts it cares about (sun banked, zombies killed, the final garden, ...).
public interface QuestCondition {
    boolean isSatisfied(QuestContext ctx);

    // How far the player has come towards this condition, for the travel log to display. Conditions
    // that count towards a number override this; the ones whose goal is a shape rather than a count
    // (an empty row, a symmetric garden, finishing on zero sun) keep the default and show no bar.
    default QuestProgress progress(Profile profile) {
        return QuestProgress.none();
    }
}
