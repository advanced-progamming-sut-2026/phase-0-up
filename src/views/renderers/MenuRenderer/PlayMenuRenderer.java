package views.renderers.MenuRenderer;

import utils.Result;

// The play menu: chapter and level selection, and the wallet.
public interface PlayMenuRenderer {
    void enterChapter(Result result);

    void enterOtherMenusFromThisMenu(String newMenuName);

    void coinsAndGemsRenderer(int n, String currencyName);

    void cheatRenderForAddingCoinsAndGems(int n, String currencyName);

    void chooseLevelRenderer(Result result);
}
