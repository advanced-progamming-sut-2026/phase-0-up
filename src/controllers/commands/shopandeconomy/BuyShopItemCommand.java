package controllers.commands.shopandeconomy;

import controllers.commands.Command;
import models.entities.plants.Plant;
import models.greenhouse.Pot;
import models.shop.Currency;
import models.shop.DailyOffer;
import models.shop.Shop;
import models.shop.ShopItem;
import models.user.Profile;
import utils.Constants;
import utils.Result;
import utils.storage.DatabaseManager;
import views.renderers.ShopRenderer;

import java.util.Random;

public class BuyShopItemCommand implements Command {
    private int itemId;
    private Shop shop;
    private int count;
    private String plantType;
    private Profile profile;
    ShopRenderer renderer;

    public BuyShopItemCommand(int itemId, Shop shop, int count, String plantType , Profile profile, ShopRenderer renderer) {
        this.itemId = itemId;
        this.shop = shop;
        this.count = count;
        this.plantType = plantType;
        this.profile = profile;
        this.renderer = renderer;
    }

    @Override
    public void execute() {
        if(itemId > 5) {
            renderer.successOfBuyingAProduct(new Result(false, "item ID is wrong"));
            return;}
        if(itemId == 5){
            DailyOffer found = shop.getDailyOffer();
            if(profile.getCoins() < 1600){renderer.successOfBuyingAProduct(new Result(false,
                    "Not enough coins for that. Go farm a few more!")); return;}
            Random rand = new Random(); found.setPurchased(true);
            int random = rand.nextInt(profile.getUnlockedPlants().size());
            String name = profile.getUnlockedPlants().get(random);
            buyARandomSeedPackInDaily(name);
            renderer.successOfBuyingAProduct(new Result(true, String.format("10 seed packets for %s have been bought!", name)));
        } else {
            ShopItem found = null;
            for(ShopItem s : shop.getPermanentItems()){
                if(s.getId() == itemId){
                    found = s; break;}
            } if(found == null) return;
            if(found.getCurrency() == Currency.COIN){
                if(profile.getCoins() < (count * found.getPrice())){
                    renderer.successOfBuyingAProduct(new Result(false, "Not enough coins for that. Go farm a few more!"));
                    return;}
            } else {
                if(profile.getGems() < (count * found.getPrice())){
                    renderer.successOfBuyingAProduct(new Result(false, "Not enough gems. Those things are precious!"));
                    return;}
            }
            if(itemId == 0 ){
                if(profile.getMyGreenHouse().isFull()) {
                    renderer.successOfBuyingAProduct(new Result(false, "the capacity of your greenhouse is full!"));
                    return;}
                for(int i = 0 ; i < count; i++) buyAPot();
            } else if(itemId == 1){
                if(count + profile.getPlantFoodCount() > 3 ){
                    renderer.successOfBuyingAProduct(new Result(false, "Your plant food jar is already brimming!"));
                    return;}
                for(int i = 0 ; i < count; i++) buyAPlantFood();
                renderer.successOfBuyingAProduct(new Result(true, String.format("Bought %d plant food(s)! now you have %d plant food(s)!",
                        count, profile.getPlantFoodCount())));
            } else if(itemId == 2){
                for(int i = 0 ; i < count; i++) buyARandomSeedPack();
            } else if(itemId == 3){
                if(!checkForContainingTheName()){renderer.successOfBuyingAProduct(new Result(false ,
                        "this plant is locked!")); return;}
                for(int i = 0 ; i < count; i++) buyASelectiveSeedPack();
                renderer.successOfBuyingAProduct(new Result(true, String.format("%d seed packets for %s have been bought!",
                        count * 10, plantType)));
            } else if(itemId == 4)
                for(int i = 0 ; i < count; i++) exchangeGemToCoin();
                renderer.successOfBuyingAProduct(new Result(true, String.format("%d gems exchanged to %d coins!\n" +
                        "Now you have %d coins and %d gems!", count * 5, count * 500, profile.getCoins(), profile.getGems())));
        }
        DatabaseManager.getInstance().saveAll();
    }

    private void buyARandomSeedPackInDaily(String name) {
        profile.spendCoins(1600);
        profile.addSeedPackets(name, 10);
    }

    private boolean checkForContainingTheName() {
        for(String p : profile.getUnlockedPlants()){
            if(p.equalsIgnoreCase(plantType)){
                return true;
            }
        }
        return false;
    }

    private void buyASelectiveSeedPack(){
        profile.spendGems(5);
        profile.addSeedPackets(plantType, 10);
    }

    private void exchangeGemToCoin() {
        profile.spendGems(5);
        profile.addCoins(500);
    }

    private void buyARandomSeedPack() {
        profile.spendCoins(1000);
        Random rand = new Random();
        int random = rand.nextInt(profile.getUnlockedPlants().size());
        String name = profile.getUnlockedPlants().get(random);
        profile.addSeedPackets(name, 5);
        renderer.successOfBuyingAProduct(new Result(true, String.format("5 seed packets for %s have been bought!",
                name)));
    }

    private void buyAPlantFood() {
        profile.spendGems(3);
        profile.addPlantFood(1);
    }

    // The greenhouse works out which pot is next -- the same call a zombie's pot drop uses, so the two
    // cannot disagree about the order. The old walk from getUnlockedPots().getLast() threw on an empty
    // greenhouse and assumed the unlocked pots were always contiguous.
    private void buyAPot() {
        profile.spendCoins(Constants.GREENHOUSE_POT_COST_COINS);
        Pot unlockedPot = profile.getMyGreenHouse().unlockNextPot();
        renderer.successOfBuyingAProduct(new Result(true,String.format("Pot at (%d, %d) unlocked successfully!",
                unlockedPot.getX(), unlockedPot.getY())));
    }
}
