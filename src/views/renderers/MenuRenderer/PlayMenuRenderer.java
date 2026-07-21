package views.renderers.MenuRenderer;

import models.user.Profile;
import utils.Result;

public class PlayMenuRenderer {
    public void enterChapter(Result result){
        System.out.println(result.message());
    }
    public void enterOtherMenusFromThisMenu(String newMenuName){
        System.out.println("Welcome to the " + newMenuName + " menu!");
    }
    public void coinsAndGemsRenderer(int n , String currencyName ){
        System.out.println("You have " + n + " " + currencyName.toLowerCase() + (n == 1 ? "" : "s") + ".");
    }
    public void cheatRenderForAddingCoinsAndGems(int n , String currencyName){
        System.out.println("Cha-ching! " + n + " " + currencyName.toLowerCase()
                + (n == 1 ? "" : "s") + " added to your wallet.");
    }
    public void chooseLevelRenderer(Result result){
        System.out.println(result.message());
    }
}
