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
        boolean isCoin = "coin".equalsIgnoreCase(currencyName == null ? "" : currencyName.trim());
        if(isCoin){
            profile.addCoins(n);
        } else {
            profile.addGems(n);
        }
        // The game says "gem" whichever word was typed: "diamond" is accepted only because the spec
        // writes the command that way, and it is never echoed back.
        renderer.cheatRenderForAddingCoinsAndGems(n , isCoin ? "coin" : "gem");

        DatabaseManager.getInstance().saveAll();
    }
}
