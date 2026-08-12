package views.console.menu;

import utils.Result;
import views.renderers.MenuRenderer.PlayMenuRenderer;

public class ConsolePlayMenuRenderer implements PlayMenuRenderer {
    @Override
    public void enterChapter(Result result){
        System.out.println(result.message());
    }

    @Override
    public void enterOtherMenusFromThisMenu(String newMenuName){
        System.out.println("Welcome to the " + newMenuName + " menu!");
    }

    @Override
    public void coinsAndGemsRenderer(int n , String currencyName ){
        System.out.println("You have " + n + " " + currencyName.toLowerCase() + (n == 1 ? "" : "s") + ".");
    }

    @Override
    public void cheatRenderForAddingCoinsAndGems(int n , String currencyName){
        System.out.println("Cha-ching! " + n + " " + currencyName.toLowerCase()
                + (n == 1 ? "" : "s") + " added to your wallet.");
    }

    @Override
    public void chooseLevelRenderer(Result result){
        System.out.println(result.message());
    }
}
