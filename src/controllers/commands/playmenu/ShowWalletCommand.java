package controllers.commands.playmenu;

import controllers.commands.Command;
import models.shop.Currency;
import models.user.Profile;
import views.renderers.MenuRenderer.PlayMenuRenderer;

public class ShowWalletCommand implements Command {
    private Profile profile;
    private Currency currency;
    private PlayMenuRenderer renderer;


    public ShowWalletCommand(Profile profile , Currency currency,  PlayMenuRenderer renderer) {
        this.profile = profile;
        this.currency = currency;
        this.renderer = renderer;
    }

    @Override
    public void execute() {
        if(currency == Currency.COIN){
            renderer.coinsAndGemsRenderer(profile.getCoins() , currency.toString());
        } else {
            renderer.coinsAndGemsRenderer(profile.getGems() , currency.toString());
        }
    }
}
