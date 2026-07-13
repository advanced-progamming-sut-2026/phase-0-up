package models.quests;

public class MainQuest extends Quest{
    @Override
    public boolean isComplete(QuestContext ctx) {
        return false;
    }

    public MainQuest() {
    }
}
