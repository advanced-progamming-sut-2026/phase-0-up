package views.renderers;

import views.OutputHandler;

// Renders a wallet balance whenever the model reports that it changed. Registered against the model's
// CurrencyObserver hook by the controller layer at start-up, so the model publishes the change and this
// class alone decides what the player actually sees.
public class CurrencyRenderer {
    public void showBalance(String currency, int newTotal) {
        OutputHandler.showMessage(currency + ": " + newTotal);
    }
}
