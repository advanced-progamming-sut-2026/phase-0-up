package controllers.commands.playmenu;

import controllers.commands.Command;
import models.game.Chapter;
import models.user.Profile;
import utils.Result;
import views.renderers.MenuRenderer.PlayMenuRenderer;

public class EnterChapterCommand implements Command {
    private String chapterName;
    private Profile profile;

    public EnterChapterCommand(String chapterName , Profile profile) {
        this.chapterName = chapterName;
        this.profile = profile;
    }

    @Override
    public void execute() {
        PlayMenuRenderer renderer = new PlayMenuRenderer();
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
