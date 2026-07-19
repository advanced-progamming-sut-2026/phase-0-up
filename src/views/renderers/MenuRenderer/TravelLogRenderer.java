package views.renderers.MenuRenderer;

import models.quests.Quest;
import views.OutputHandler;

import java.util.List;

// Renders the Travel Log: a quest page (quests already priority-sorted by the QuestSystem) or the
// mini-games page.
public class TravelLogRenderer {

    public void showQuestPage(String pageTitle, List<Quest> quests) {
        OutputHandler.showMessage("=== Travel Log: " + pageTitle + " ===");
        if (quests == null || quests.isEmpty()) {
            OutputHandler.showMessage("  (no quests on this page)");
            return;
        }
        int i = 1;
        for (Quest quest : quests) {
            OutputHandler.showMessage(i++ + ". [" + quest.getPriority() + "] " + quest.getName()
                    + (quest.isComplete() ? (quest.isClaimed() ? " (claimed)" : " (complete!)") : ""));
            OutputHandler.showMessage("     " + quest.getDescription());
            OutputHandler.showMessage("     Reward: " + quest.getReward().describe());
        }
    }

    public void showMinigamesPage(List<String> minigames) {
        OutputHandler.showMessage("=== Travel Log: Mini-games ===");
        if (minigames == null || minigames.isEmpty()) {
            OutputHandler.showMessage("  (no mini-games available)");
            return;
        }
        int i = 1;
        for (String minigame : minigames) {
            OutputHandler.showMessage(i++ + ". " + minigame);
        }
    }

    public void unknownPage(String pageName) {
        OutputHandler.showMessage("No travel-log page named \"" + pageName
                + "\". Try: main, daily, epic, minigames, all.");
    }
}
