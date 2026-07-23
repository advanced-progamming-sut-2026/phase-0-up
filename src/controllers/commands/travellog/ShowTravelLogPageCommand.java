package controllers.commands.travellog;

import controllers.commands.Command;
import controllers.systems.game.QuestSystem;
import models.quests.Quest;
import models.user.Profile;
import views.renderers.MenuRenderer.TravelLogRenderer;

import java.util.List;

// Handles "travel log page <page_name>": shows one page of the travel log. Quest pages come straight
// from the QuestSystem already ranked by priority; the mini-games page lists the extra mini-game
// levels the travel log hosts.
public class ShowTravelLogPageCommand implements Command {
    private static final List<String> MINIGAMES =
            List.of("Vasebreaker", "I, Zombie", "Wall-nut Bowling", "Beghouled", "Zombotany");

    private final String pageName;
    private final QuestSystem questSystem;
    private final Profile profile;
    private final TravelLogRenderer renderer;

    public ShowTravelLogPageCommand(String pageName, QuestSystem questSystem, Profile profile,
                                    TravelLogRenderer renderer) {
        this.pageName = pageName;
        this.questSystem = questSystem;
        this.profile = profile;
        this.renderer = renderer;
    }

    @Override
    public void execute() {
        String page = pageName == null ? "" : pageName.toLowerCase().trim();
        switch (page) {
            case "main":
            case "story":
                renderer.showQuestPage("Main Quests", questSystem.getQuestsForPage(Quest.Category.MAIN, profile), profile);
                break;
            case "daily":
                renderer.showQuestPage("Daily Quests", questSystem.getQuestsForPage(Quest.Category.DAILY, profile), profile);
                break;
            case "epic":
            case "challenges":
                renderer.showQuestPage("Epic Challenges", questSystem.getQuestsForPage(Quest.Category.EPIC, profile), profile);
                break;
            case "all":
                renderer.showQuestPage("All Quests", questSystem.getSortedQuestsForLog(profile), profile);
                break;
            case "minigames":
            case "mini-games":
                renderer.showMinigamesPage(MINIGAMES);
                break;
            default:
                renderer.unknownPage(pageName);
        }
    }
}
