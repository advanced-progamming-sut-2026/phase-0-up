package controllers.commands.shopandeconomy;

import controllers.commands.Command;
import models.shop.Shop;
import views.renderers.ShopRenderer;

public class ShowShopCommand implements Command {
    private String type;
    private Shop shop;

    public ShowShopCommand(String type , Shop shop) {
        this.type = type;
        this.shop = shop;
    }

    @Override
    public void execute() {
        ShopRenderer renderer = new ShopRenderer();
        switch (type){
            case "list": {
                renderer.listAllProducts(shop.showPermanentItems());
                break;
            }
            case "daily":{
                renderer.listDailyProducts(shop.showDailyOffer());
                break;
            }
        }
    }
}
