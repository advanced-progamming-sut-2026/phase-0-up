package models.quests.QuestCondition;

public class NoLossCondition implements QuestCondition {
    @Override
    public boolean isSatisfied(QuestCondition ctx) {
        return false;
    }
}
