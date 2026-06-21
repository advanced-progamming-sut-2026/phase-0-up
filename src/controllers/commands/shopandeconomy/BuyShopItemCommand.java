package controllers.commands.shopandeconomy;

import controllers.commands.Command;
import models.greenhouse.Pot;
import models.shop.DailyOffer;
import models.shop.Shop;
import models.shop.ShopItem;
import models.user.Profile;
import utils.Result;
import views.renderers.ShopRenderer;

import java.util.Random;

public class BuyShopItemCommand implements Command {
    private int itemId;
    private Shop shop;
    private int count;
    private String plantType;
    private Profile profile;

    public BuyShopItemCommand(int itemId, Shop shop, int count, String plantType , Profile profile) {
        this.itemId = itemId;
        this.shop = shop;
        this.count = count;
        this.plantType = plantType;
        this.profile = profile;
    }

    @Override
    public void execute() {
        ShopRenderer renderer = new ShopRenderer();
        if(itemId > 5) {
            renderer.successOfBuyingAProduct(new Result(false, "item ID is wrong"));
            return;}
        if(itemId == 5){
            DailyOffer found = shop.getDailyOffer();

        } else {
            ShopItem found = null;
            for(ShopItem s : shop.getPermanentItems()){
                if(s.getId() == itemId){
                    found = s; break;}
            }
            if(itemId == 0 ){
                if(profile.getMyGreenHouse().getUnlockedPots().size() == 20) {
                    renderer.successOfBuyingAProduct(new Result(false, "the capacity of your greenhouse is full!"));
                    return;}
                if(profile.getCoins() < (count * found.getPrice())){
                    renderer.successOfBuyingAProduct(new Result(false, "you don't have enough coins!"));
                    return;}
                for(int i = 0 ; i < count; i++) buyAPot();
            } else if(itemId == 1){
                if(count + profile.getPlantFoodCount() > 3 ){
                    renderer.successOfBuyingAProduct(new Result(false, "the capacity of your plant food is full!"));
                    return;}
                if(profile.getGems() < (count * found.getPrice())){
                    renderer.successOfBuyingAProduct(new Result(false, "you don't have enough gems!"));
                    return;}
                for(int i = 0 ; i < count; i++) buyAPlantFood();
            } else if(itemId == 2){
                if(profile.getCoins() < (count * found.getPrice())){
                    renderer.successOfBuyingAProduct(new Result(false, "you don't have enough coins!"));
                    return;}
                for(int i = 0 ; i < count; i++) buyASeedPack();
            } else if(itemId == 3){

            } else if(itemId == 4){

            }
        }
    }

    private void buyASeedPack() {
        profile.spendCoins(1000);
        Random rand = new Random();
        int random = rand.nextInt(profile.getUnlockedPlants().size());
        String name = profile.getUnlockedPlants().get(random).getName();
        profile.getOwnedSeedPackets().merge(name , 5 , Integer::sum);
    }

    private void buyAPlantFood() {
        profile.spendGems(3);
        profile.setPlantFoodCount(profile.getPlantFoodCount()+1);
    }

    private void buyAPot() {
        profile.spendCoins(2000);
        if(profile.getMyGreenHouse().getUnlockedPots().isEmpty()){
            Pot e = new Pot(1 , 1);
            profile.getMyGreenHouse().getUnlockedPots().add(e); return;
        }
        Pot lastPot = profile.getMyGreenHouse().getUnlockedPots().getLast();
        if(lastPot.getY() != 4){
            Pot e = new Pot(lastPot.getX() , lastPot.getY()+1);
            profile.getMyGreenHouse().getUnlockedPots().add(e); return;
        } else {
            Pot e = new Pot(lastPot.getX()+1 , 1);
            profile.getMyGreenHouse().getUnlockedPots().add(e); return;
        }
    }
}
