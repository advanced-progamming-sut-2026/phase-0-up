package controllers.commands.shopandeconomy;

import controllers.commands.Command;
import models.entities.plants.Plant;
import models.greenhouse.Pot;
import models.greenhouse.PotState;
import models.shop.Currency;
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
                    "you don't have enough coins!")); return;}
            Random rand = new Random(); found.setPurchased(true);
            int random = rand.nextInt(profile.getUnlockedPlants().size());
            String name = profile.getUnlockedPlants().get(random);
            buyARandomSeedPackInDaily(name);
        } else {
            ShopItem found = null;
            for(ShopItem s : shop.getPermanentItems()){
                if(s.getId() == itemId){
                    found = s; break;}
            } if(found == null) return;
            if(found.getCurrency() == Currency.COIN){
                if(profile.getCoins() < (count * found.getPrice())){
                    renderer.successOfBuyingAProduct(new Result(false, "you don't have enough coins!"));
                    return;}
            } else {
                if(profile.getGems() < (count * found.getPrice())){
                    renderer.successOfBuyingAProduct(new Result(false, "you don't have enough gems!"));
                    return;}
            }
            if(itemId == 0 ){
                if(profile.getMyGreenHouse().getUnlockedPots().size() == 20) {
                    renderer.successOfBuyingAProduct(new Result(false, "the capacity of your greenhouse is full!"));
                    return;}
                for(int i = 0 ; i < count; i++) buyAPot();
            } else if(itemId == 1){
                if(count + profile.getPlantFoodCount() > 3 ){
                    renderer.successOfBuyingAProduct(new Result(false, "the capacity of your plant food is full!"));
                    return;}
                for(int i = 0 ; i < count; i++) buyAPlantFood();
            } else if(itemId == 2){
                for(int i = 0 ; i < count; i++) buyARandomSeedPack();
            } else if(itemId == 3){
                if(!checkForContainingTheName()){renderer.successOfBuyingAProduct(new Result(false ,
                        "this plant is locked!")); return;}
                for(int i = 0 ; i < count; i++) buyASelectiveSeedPack();
            } else if(itemId == 4)
                for(int i = 0 ; i < count; i++) exchangeGemToCoin();
        }
        renderer.successOfBuyingAProduct(new Result(true , "your shopping finished successfully!"));
    }

    private void buyARandomSeedPackInDaily(String name) {
        profile.spendCoins(1600);
        profile.getOwnedSeedPackets().keySet().stream()
                .filter(packet -> packet.getPlantType().equals(name))
                .findFirst()
                .ifPresent(packet -> profile.getOwnedSeedPackets().merge(packet, 10, Integer::sum));
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
        profile.getOwnedSeedPackets().keySet().stream()
                .filter(packet -> packet.getPlantType().equals(plantType.toLowerCase()))
                .findFirst()
                .ifPresent(packet -> profile.getOwnedSeedPackets().merge(packet, 10, Integer::sum));
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
        profile.getOwnedSeedPackets().keySet().stream()
                .filter(packet -> packet.getPlantType().equals(name))
                .findFirst()
                .ifPresent(packet -> profile.getOwnedSeedPackets().merge(packet, 5, Integer::sum));
    }

    private void buyAPlantFood() {
        profile.spendGems(3);
        profile.addPlantFood(1);
    }

    private void buyAPot() {
        profile.spendCoins(2000);
        Pot lastPot = profile.getMyGreenHouse().getUnlockedPots().getLast();

        if(lastPot.getX() != 4) {
            profile.getMyGreenHouse()
                    .getPot(lastPot.getX() + 1, lastPot.getY()).setState(PotState.EMPTY);
        }
        else
            profile.getMyGreenHouse().getPot(1, lastPot.getY() + 1).setState(PotState.EMPTY);
    }
}
