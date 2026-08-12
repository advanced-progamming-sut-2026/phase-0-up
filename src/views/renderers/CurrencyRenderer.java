package views.renderers;

// Renders a wallet balance whenever the model reports that it changed. Registered against the model's
// CurrencyObserver hook by the composition root at start-up, so the model publishes the change and the
// chosen implementation alone decides what the player actually sees.
public interface CurrencyRenderer {
    void showBalance(String currency, int newTotal);
}
