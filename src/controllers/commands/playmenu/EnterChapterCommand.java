package controllers.commands.playmenu;

import controllers.commands.Command;
import models.game.Chapter;
import models.user.Profile;
import utils.Result;
import views.renderers.MenuRenderer.PlayMenuRenderer;

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
        for(Chapter ch : profile.getUnlockedChapters()){
            if(ch.getName().equals(chapterName)){
                profile.setCurrentChapter(ch);
                renderer.enterChapter(new Result(true , "chapter changed successfully!"));
                return;
            }
        }
        renderer.enterChapter(new Result(false , "this chapter is unavailable!"));
    }
}
