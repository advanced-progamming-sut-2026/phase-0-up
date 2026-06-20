package controllers.commands.playmenu;

import controllers.commands.Command;
import models.user.Profile;
import views.renderers.MenuRenderer.PlayMenuRenderer;

public class CheatAddCommand implements Command {
    private String currencyName;
    private int n;
    private Profile profile;

    public CheatAddCommand(String currencyName, int n , Profile profile) {
        this.currencyName = currencyName;
        this.n = n;
        this.profile = profile;
    }

    @Override
    public void execute() {
        PlayMenuRenderer renderer = new PlayMenuRenderer();
        if(currencyName.equals("coin")){
            profile.addCoins(n);
        } else {
            profile.addGems(n);
        }
        renderer.cheatRenderForAddingCoinsAndGems(n , currencyName);
    }
}
