package views.renderers.MenuRenderer;

import models.quests.Quest;
import models.quests.QuestProgress;
import models.user.Profile;
import views.OutputHandler;

import java.util.List;

// Renders the Travel Log: a quest page (quests already priority-sorted by the QuestSystem) or the
// mini-games page.
public class TravelLogRenderer {

    public void showQuestPage(String pageTitle, List<Quest> quests) {
        showQuestPage(pageTitle, quests, null);
    }

    // The profile is what turns this page from a static list into a progress report: every quest that
    // counts towards a number reports where the player stands against it.
    public void showQuestPage(String pageTitle, List<Quest> quests, Profile profile) {
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

            QuestProgress progress = quest.getProgress(profile);
            String progressLine = progress == null ? null : progress.describe();
            if (progressLine != null) {
                OutputHandler.showMessage("     " + progressLine);
            }

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

    // Announces a mini-game launch (or that one is not playable yet).
    public void launchingMinigame(String name, int difficulty) {
        OutputHandler.showMessage("Launching " + name + " (difficulty " + difficulty
                + "). Don't let a zombie reach your house!");
    }

    public void minigameUnavailable(String name) {
        OutputHandler.showMessage("No mini-game called \"" + name + "\". Playable now: vasebreaker, "
                + "bowling, izombie, beghouled, zombotany.");
    }
}
