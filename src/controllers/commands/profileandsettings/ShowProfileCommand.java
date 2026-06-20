package controllers.commands.profileandsettings;

import controllers.commands.Command;
import models.user.Profile;
import models.user.User;
import views.renderers.MenuRenderer.ProfileMenuRenderer;

public class ShowProfileCommand implements Command {
    private User user;

    public ShowProfileCommand(User currentUser) {
        this.user = currentUser;
    }

    @Override
    public void execute() {
        StringBuilder sb = new StringBuilder();
        sb.append("============ SHOW INFO ============\n");
        sb.append("Username : ").append(user.getUsername()).append("\n");
        sb.append("Nickname : ").append(user.getNickname()).append("\n");
        sb.append("Game Played : ").append(user.getProfile().getGameNumbers()).append("\n");
        sb.append("Coins : ").append(user.getProfile().getCoins()).append("\n");
        sb.append("Gems : ").append(user.getProfile().getGems()).append("\n");
        int passed = (user.getProfile().getLastChapter()-1) * 4 + user.getProfile().getLastLevel()-1;
        sb.append("Completed Levels : ").append(passed).append("\n");
        sb.append("Max Number Of MeowPoints : ").append(user.getProfile().getBestNumberOfMeowPoints()).append("\n");
        new ProfileMenuRenderer().showInfo(sb.toString());
        return;
    }
}
