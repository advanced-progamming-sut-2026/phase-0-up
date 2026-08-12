package views.renderers.MenuRenderer;

import models.quests.Quest;
import models.user.Profile;

import java.util.List;

// The Travel Log: a quest page (quests arrive already priority-sorted by the QuestSystem) or the
// mini-games page.
public interface TravelLogRenderer {

    // Without a profile the page is a static list; with one, every quest that counts towards a number
    // can report where the player stands against it.
    void showQuestPage(String pageTitle, List<Quest> quests, Profile profile);

    // Convenience for callers that have no profile to hand. Deliberately a default: it is the same
    // page, not a second rendering decision, so no implementation should have to restate it.
    default void showQuestPage(String pageTitle, List<Quest> quests) {
        showQuestPage(pageTitle, quests, null);
    }

    void showMinigamesPage(List<String> minigames);

    void unknownPage(String pageName);

    void launchingMinigame(String name, int difficulty);

    void minigameUnavailable(String name);
}
