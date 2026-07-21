package models.user;

// How the model announces that a currency balance moved, without knowing anything about the view.
//
// This is the MVC seam for wallet output: Profile publishes the fact that coins/diamonds changed, the
// controller layer registers an implementation at start-up, and the view decides how it is rendered.
// Profile therefore never imports or calls a renderer -- the dependency points from the controller into
// both layers instead of from the model into the view.
@FunctionalInterface
public interface CurrencyObserver {
    void onBalanceChanged(String currency, int newTotal);
}
