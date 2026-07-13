package models.quests;

public class EpicQuest extends Quest{
    @Override
    public boolean isComplete(QuestContext ctx) {
        return false;
    }
}
