package models.shop;

import java.util.ArrayList;
import java.util.List;

public class Shop {
    private List<ShopItem> permanentItems;
    private DailyOffer dailyOffer;

    public Shop() {
        ShopItem pot = new ShopItem(0 , "pot" , 2000 , Currency.COIN , 1);
        ShopItem plantFood = new ShopItem(1 , "plantFood" , 3 , Currency.GEM , 1);
        ShopItem randomSeedPacket = new ShopItem(2 , "random" , 1000 , Currency.COIN , 5);
        ShopItem selectiveSeedPacket = new ShopItem(3 , "selective" , 5 , Currency.GEM , 10);
        ShopItem exchange = new ShopItem(4 , "exchange" , 5 , Currency.GEM , 500);
        permanentItems = new ArrayList<>();
        permanentItems.add(pot); permanentItems.add(plantFood);
        permanentItems.add(randomSeedPacket); permanentItems.add(selectiveSeedPacket);
        permanentItems.add(exchange);
        dailyOffer = new DailyOffer(5 , 0 , "random" , 2000 , 1600 , 10);
    }

    // Rotates the offer's CONTENT. Nothing calls it, and it is not what resets the deal each day --
    // whether today's has been taken lives on the Profile, which rolls itself over on the date. See
    // DailyOffer.
    public void updateDailyOOffer(){
        int i = dailyOffer.getDate();
        dailyOffer = new DailyOffer(5 , i+1 , "random" , 2000 , 1600 , 10);
    }
    public String showPermanentItems(){
        StringBuilder sb = new StringBuilder();
        sb.append("========= SHOP PRODUCTS =========\n");
        for(ShopItem s : permanentItems){
            sb.append("ID : ").append(s.getId()).append("\n");
            sb.append("Name : ").append(s.getName()).append("\n");
            sb.append("Price : ").append(s.getPrice()).append(" ").append(s.getCurrency().toString()).append("\n");
            sb.append("Number of the product : ").append(s.getCapacity()).append("\n");
        }
        return sb.toString();
    }
    // `boughtToday` comes from the Profile, because that is where it is kept and persisted -- the offer
    // itself is the same for everyone, only whether it has been taken is per player.
    public String showDailyOffer(boolean boughtToday){
        StringBuilder sb = new StringBuilder();
        sb.append("========= DAILY OFFER =========\n");
        DailyOffer s = dailyOffer;
            sb.append("ID : ").append(s.getId()).append("\n");
            // What you actually get. The listing used to quote two prices and a boolean and never once
            // say what was on offer.
            sb.append("Offer : ").append(s.getPackets())
                    .append(" seed packets for a plant you have already unlocked\n");
            sb.append("Base Price : ").append(s.getBasePrice()).append(" Coins\n");
            sb.append("Discount Price : ").append(s.getDiscountPrice()).append(" Coins\n");
            sb.append("Is purchased : ").append(boughtToday).append("\n");
        return sb.toString();
    }

    public DailyOffer getDailyOffer() {
        return dailyOffer;
    }

    public List<ShopItem> getPermanentItems() {
        return permanentItems;
    }
}
