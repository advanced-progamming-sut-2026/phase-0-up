package controllers.commands.playmenu;

import controllers.commands.Command;
import models.game.Chapter;
import models.user.Profile;
import utils.Result;
import views.renderers.MenuRenderer.PlayMenuRenderer;

import java.util.List;

public class EnterChapterCommand implements Command {
    private String chapterName;
    private Profile profile;
    private PlayMenuRenderer renderer;

    public EnterChapterCommand(String chapterName , Profile profile, PlayMenuRenderer renderer) {
        this.chapterName = chapterName;
        this.profile = profile;
        this.renderer = renderer;
    }

    @Override
    public void execute() {
        Chapter target = resolveChapter();
        if (target != null) {
            profile.setCurrentChapter(target);
            renderer.enterChapter(new Result(true , "chapter changed successfully!"));
            return;
        }
        renderer.enterChapter(new Result(false , "this chapter is unavailable!"));
    }

    // Accepts either a 1-based chapter number (like "level -l <n>") or the chapter's name/key
    // (e.g. ANCIENT_EGYPT). Unlocked chapters are stored in campaign order, so the Nth unlocked chapter
    // is chapter N; a number beyond that range is still locked -> unavailable.
    private Chapter resolveChapter() {
        List<Chapter> unlocked = profile.getUnlockedChapters();
        if (unlocked == null) {
            return null;
        }
        if (chapterName != null && chapterName.matches("\\d+")) {
            int index = Integer.parseInt(chapterName) - 1;
            return (index >= 0 && index < unlocked.size()) ? unlocked.get(index) : null;
        }
        for (Chapter ch : unlocked) {
            if (ch.getName() != null && ch.getName().equalsIgnoreCase(chapterName)) {
                return ch;
            }
        }
        return null;
    }
}
