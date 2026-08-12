package views.console;

import utils.Result;
import views.OutputHandler;
import views.renderers.ShopRenderer;

// Prints the shop listing to stdout. The terminal build's ShopRenderer.
public class ConsoleShopRenderer implements ShopRenderer {
    @Override
    public void listAllProducts(String out) {
        OutputHandler.showMessage(out);
    }

    @Override
    public void listDailyProducts(String out) {
        OutputHandler.showMessage(out);
    }

    @Override
    public void successOfBuyingAProduct(Result result) {
        OutputHandler.showMessage(result.message());
    }
}
