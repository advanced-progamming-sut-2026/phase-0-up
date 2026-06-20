package controllers.commands.shopandeconomy;

import controllers.commands.Command;
import models.shop.Shop;

public class BuyShopItemCommand implements Command {
    private int itemId;
    private Shop shop;
    private int count;
    private String plantType;

    @Override
    public void execute() {}
}
