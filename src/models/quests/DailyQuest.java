package models.quests;

public class DailyQuest extends Quest{
    @Override
    public boolean isComplete(QuestContext ctx) {
        return false;
    }

    public DailyQuest() {
    }
}
