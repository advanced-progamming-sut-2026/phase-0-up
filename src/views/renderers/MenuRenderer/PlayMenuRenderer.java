package views.renderers.MenuRenderer;

import models.user.Profile;
import utils.Result;

public class PlayMenuRenderer {
    public void enterChapter(Result result){
        System.out.println(result.message());
    }
    public void enterOtherMenusFromThisMenu(String newMenuName){
        System.out.println("you are now in : " + newMenuName + " menu!");
    }
    public void coinsAndGemsRenderer(int n , String currencyName ){
        System.out.println(currencyName.toUpperCase() + " number : " + n);
    }
    public void cheatRenderForAddingCoinsAndGems(int n , String currencyName){
        System.out.println(n + " added to your " + currencyName + "s");
    }
    public void chooseLevelRenderer(Result result){
        System.out.println(result.message());
    }
}
