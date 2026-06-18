package models.templates;

import java.util.List;

public class QuestTemplate {
    private String name;
    private String description;
    private String category;
    private String condition;
    private String rewardType;
    private int rewardAmount;
    private String priority;
    private List<String> variables;

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getCategory() {
        return category;
    }

    public String getCondition() {
        return condition;
    }

    public String getRewardType() {
        return rewardType;
    }

    public int getRewardAmount() {
        return rewardAmount;
    }

    public String getPriority() {
        return priority;
    }

    public List<String> getVariables() {
        return variables;
    }
}
