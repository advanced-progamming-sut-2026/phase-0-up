package controllers.commands.shopandeconomy;

import controllers.commands.Command;
import models.shop.Shop;
import views.renderers.ShopRenderer;

public class ShowShopCommand implements Command {
    private String type;
    private Shop shop;
    ShopRenderer renderer;
    public ShowShopCommand(String type , Shop shop, ShopRenderer renderer) {
        this.type = type;
        this.shop = shop;
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
                renderer.listDailyProducts(shop.showDailyOffer());
                break;
            }
        }
    }
}
