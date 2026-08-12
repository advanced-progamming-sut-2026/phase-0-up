package views.gdx.renderers;

import views.gdx.core.ToastSink;
import views.renderers.CurrencyRenderer;

// Announces a wallet change as a toast.
//
// Profile.setCurrencyObserver is a single static hook, so exactly one of these may hold it -- the
// composition root decides which, and the terminal and graphical builds never run in the same JVM.
// When the Store and wallet HUD land in Phase 5 this becomes their update trigger rather than a toast.
public final class GdxCurrencyRenderer implements CurrencyRenderer {

    private final ToastSink toasts;

    public GdxCurrencyRenderer(ToastSink toasts) {
        this.toasts = toasts;
    }

    @Override
    public void showBalance(String currency, int newTotal) {
        toasts.info(currency + ": " + newTotal);
    }
}
