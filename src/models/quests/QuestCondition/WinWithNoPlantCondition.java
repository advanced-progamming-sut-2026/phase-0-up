package models.quests.QuestCondition;

public class WinWithNoPlantCondition implements QuestCondition{

    @Override
    public boolean isSatisfied(QuestCondition ctx) {
        return false;
    }
}
