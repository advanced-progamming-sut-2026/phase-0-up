package controllers.commands.profileandsettings;

import controllers.commands.Command;
import models.user.User;
import views.renderers.MenuRenderer.SettingMenuRenderer;

public class ChangeDifficultyCommand implements Command {
    private User currentUser;
    private int difficulty;

    public ChangeDifficultyCommand(User currentUser, int difficulty) {
        this.currentUser = currentUser;
        this.difficulty = difficulty;
    }

    @Override
    public void execute() {
        SettingMenuRenderer renderer = new SettingMenuRenderer();
        if(difficulty < 1 || difficulty > 5){
            renderer.changeDL(false , difficulty);
            return;
        }
        currentUser.getProfile().setDifficultyLevel(difficulty);
        renderer.changeDL(true , difficulty);
    }
}
