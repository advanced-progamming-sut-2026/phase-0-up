package views.gdx.renderers;

import utils.Result;
import views.gdx.core.ToastSink;
import views.renderers.ShopRenderer;

// The shop. The listings arrive as pre-formatted multi-line text built for a terminal, which is not
// something a toast can show; StoreScreen (T5.6) is what actually renders them. Until then the listing
// commands report that they were understood, and a purchase -- a single sentence -- toasts properly.
public final class GdxShopRenderer implements ShopRenderer {

    private final ToastSink toasts;

    public GdxShopRenderer(ToastSink toasts) {
        this.toasts = toasts;
    }

    @Override
    public void listAllProducts(String out) {
        toasts.info("The shop is open -- browse it from the Store screen.");
    }

    @Override
    public void listDailyProducts(String out) {
        toasts.info("Today's deals are in -- see the Store screen.");
    }

    @Override
    public void successOfBuyingAProduct(Result result) {
        toasts.show(result);
    }
}
