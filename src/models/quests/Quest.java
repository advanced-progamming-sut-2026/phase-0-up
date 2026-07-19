package models.quests;

import models.quests.QuestCondition.QuestCondition;
import models.quests.Reward.Reward;
import models.user.Profile;

// A live quest: its identity, category, priority, and reward, plus its completion/claim state.
//
// Completion detection (evaluating the quest's gameplay condition) is a separate concern driven by
// the tracking layer, which calls markComplete() when the condition is met. This class owns the
// reward hand-off: claim() grants the reward exactly once, cleanly through the profile.
public abstract class Quest {
    public enum Category { DAILY, MAIN, EPIC }

    private final String id;
    private final String name;
    private final String description;
    private final Category category;
    private final QuestPriority priority;
    private final Reward reward;
    private final String variables;

    private QuestCondition condition;
    private boolean completed;
    private boolean claimed;

    protected Quest(String id, String name, String description, Category category,
                    QuestPriority priority, Reward reward, String variables) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.priority = priority;
        this.reward = reward;
        this.variables = variables;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Category getCategory() { return category; }
    public QuestPriority getPriority() { return priority; }
    public Reward getReward() { return reward; }
    public String getVariables() { return variables; }

    public boolean isComplete() { return completed; }
    public boolean isClaimed() { return claimed; }

    public QuestCondition getCondition() { return condition; }
    public void setCondition(QuestCondition condition) { this.condition = condition; }

    // Whether this quest's completion condition is satisfied by a finished level. Used by the
    // QuestSystem to decide, at level end, which quests just completed.
    public boolean isSatisfiedBy(QuestContext ctx) {
        return condition != null && condition.isSatisfied(ctx);
    }

    // Marked complete by the tracking layer once this quest's condition is satisfied in play.
    public void markComplete() { this.completed = true; }

    // Grants the reward once, when the quest is complete and has not been claimed yet. Returns whether
    // it actually granted, so the caller can report it.
    public boolean claim(Profile profile) {
        if (!completed || claimed || profile == null) {
            return false;
        }
        reward.grant(profile);
        claimed = true;
        return true;
    }
}
