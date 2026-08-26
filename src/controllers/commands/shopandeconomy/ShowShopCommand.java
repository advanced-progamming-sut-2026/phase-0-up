package controllers.commands.shopandeconomy;

import controllers.commands.Command;
import models.shop.Shop;
import models.user.Profile;
import views.renderers.ShopRenderer;

public class ShowShopCommand implements Command {
    private String type;
    private Shop shop;
    // Only for "has today's deal already been taken", which is per-player state and therefore not the
    // Shop's to answer. Null is tolerated and read as "not yet": listing the stock is not worth failing
    // over if there is somehow nobody signed in.
    private Profile profile;
    ShopRenderer renderer;
    public ShowShopCommand(String type , Shop shop, Profile profile, ShopRenderer renderer) {
        this.type = type;
        this.shop = shop;
        this.profile = profile;
        this.renderer = renderer;
    }

    @Override
    public void execute() {
        switch (type){
            case "list": {
                renderer.listAllProducts(shop.showPermanentItems());
                break;
            }
            case "daily":{
                renderer.listDailyProducts(shop.showDailyOffer(
                        profile != null && profile.isHasBoughtDailyOfferToday()));
                break;
            }
        }
    }
}
