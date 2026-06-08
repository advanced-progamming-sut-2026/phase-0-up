package controllers.systems;

import models.user.Profile;

public class ShopSystem {
    private static ShopSystem instance;

    private ShopSystem() {}

    public static synchronized ShopSystem getInstance() {
        if (instance == null) {
            instance = new ShopSystem();
        }
        return instance;
    }

    public void refreshDailyOffers(Profile profile){}
    public void buyItem(Profile profile, String itemName, int count){}
    private boolean hasEnoughCurrency(Profile p, Currency c, int cost){return false;}
    private boolean isAtCapacity(Profile p, String itemId, int count){return false;}
    private void deductCurrency(Profile p, Currency c, int cost){}
    private void grantItem(Profile p, String itemId, int count, String type){}
}
