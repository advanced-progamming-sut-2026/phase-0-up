package views.gdx.renderers;

import utils.Result;
import views.gdx.core.ToastSink;
import views.renderers.MenuRenderer.PlayMenuRenderer;

// The play menu: chapters, levels and the wallet.
public final class GdxPlayMenuRenderer implements PlayMenuRenderer {

    private final ToastSink toasts;

    public GdxPlayMenuRenderer(ToastSink toasts) {
        this.toasts = toasts;
    }

    @Override
    public void enterChapter(Result result) {
        toasts.show(result);
    }

    @Override
    public void enterOtherMenusFromThisMenu(String newMenuName) {
        toasts.info("Welcome to the " + newMenuName + " menu!");
    }

    @Override
    public void coinsAndGemsRenderer(int n, String currencyName) {
        // The wallet is permanently on screen once the Store lands (T5.6); asking for a balance the
        // player can already read would just be noise.
    }

    @Override
    public void cheatRenderForAddingCoinsAndGems(int n, String currencyName) {
        toasts.success("Cha-ching! " + n + " " + currencyName.toLowerCase()
                + (n == 1 ? "" : "s") + " added to your wallet.");
    }

    @Override
    public void chooseLevelRenderer(Result result) {
        toasts.show(result);
    }
}
