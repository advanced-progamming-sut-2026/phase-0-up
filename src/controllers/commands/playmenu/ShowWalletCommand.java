package controllers.commands.playmenu;

import controllers.commands.Command;
import models.shop.Currency;
import models.user.Profile;
import views.renderers.MenuRenderer.PlayMenuRenderer;

public class ShowWalletCommand implements Command {
    private Profile profile;
    private Currency currency;

    public ShowWalletCommand(Profile profile , Currency currency) {
        this.profile = profile;
        this.currency = currency;
    }

    @Override
    public void execute() {
        PlayMenuRenderer renderer = new PlayMenuRenderer();
        if(currency == Currency.COIN){
            renderer.coinsAndGemsRenderer(profile.getCoins() , currency.toString());
        } else {
            renderer.coinsAndGemsRenderer(profile.getGems() , currency.toString());
        }
    }
}
