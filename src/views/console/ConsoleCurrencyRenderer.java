package views.console;

import views.OutputHandler;
import views.renderers.CurrencyRenderer;

// Prints the new balance whenever the model reports one. The terminal build's CurrencyRenderer.
public class ConsoleCurrencyRenderer implements CurrencyRenderer {
    @Override
    public void showBalance(String currency, int newTotal) {
        OutputHandler.showMessage(currency + ": " + newTotal);
    }
}
