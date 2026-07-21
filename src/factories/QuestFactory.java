package factories;

import models.quests.DailyQuest;
import models.quests.EpicQuest;
import models.quests.MainQuest;
import models.quests.Quest;
import models.quests.QuestCondition.ConditionFactory;
import models.quests.QuestCondition.QuestCondition;
import models.quests.QuestPriority;
import models.quests.Reward.Reward;
import models.quests.Reward.RewardFactory;
import models.templates.QuestTemplate;
import utils.registry.QuestRegistry;

// Builds a live Quest from a registered blueprint: picks the quest class from its category, parses
// the priority, and wires in the reward strategy (via RewardFactory). This is the single place that
// turns authored quest data into runnable quest objects, so the engine never hardcodes a quest.
public final class QuestFactory {
    private QuestFactory() { }

    public static Quest createQuest(String id) {
        return createQuest(QuestRegistry.getInstance().getQuestTemplateById(id));
    }

    public static Quest createQuest(QuestTemplate template) {
        if (template == null) {
            return null;
        }
        QuestPriority priority = parsePriority(template.getPriority());
        Reward reward = RewardFactory.create(template.getReward());
        QuestCondition condition = ConditionFactory.create(template.getCondition());
        String category = template.getCategory() == null ? "DAILY" : template.getCategory().toUpperCase();

        // Interpolate placeholders (bare "n", {plant}, {family}, ...) with the condition's real values
        // now, so the stored description is display-ready and every reader shows the concrete numbers.
        String description = models.quests.QuestText.interpolate(
                template.getDescription(), template.getCondition());

        Quest quest;
        switch (category) {
            case "MAIN":
                quest = new MainQuest(template.getId(), template.getName(), description,
                        priority, reward, template.getVariables());
                break;
            case "EPIC":
                quest = new EpicQuest(template.getId(), template.getName(), description,
                        priority, reward, template.getVariables());
                break;
            case "DAILY":
            default:
                quest = new DailyQuest(template.getId(), template.getName(), description,
                        priority, reward, template.getVariables());
        }
        quest.setCondition(condition);
        return quest;
    }

    // Unknown or blank priorities fall back to LOW so a mis-authored quest sinks to the bottom of the
    // log rather than crashing the sort.
    private static QuestPriority parsePriority(String raw) {
        if (raw == null) {
            return QuestPriority.LOW;
        }
        try {
            return QuestPriority.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return QuestPriority.LOW;
        }
    }
}
