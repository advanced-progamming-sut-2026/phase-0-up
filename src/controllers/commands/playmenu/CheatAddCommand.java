package controllers.commands.playmenu;

import controllers.commands.Command;
import models.user.Profile;
import utils.storage.DatabaseManager;
import views.renderers.MenuRenderer.PlayMenuRenderer;

public class CheatAddCommand implements Command {
    private String currencyName;
    private int n;
    private Profile profile;
    PlayMenuRenderer renderer;


    public CheatAddCommand(String currencyName, int n , Profile profile,  PlayMenuRenderer renderer) {
        this.currencyName = currencyName;
        this.n = n;
        this.profile = profile;
        this.renderer = renderer;
    }

    @Override
    public void execute() {
        if(currencyName.equals("coin")){
            profile.addCoins(n);
        } else {
            profile.addGems(n);
        }
        renderer.cheatRenderForAddingCoinsAndGems(n , currencyName);

        DatabaseManager.getInstance().saveAll();
    }
}
