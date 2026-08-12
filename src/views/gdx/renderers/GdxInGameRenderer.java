package views.gdx.renderers;

import utils.Result;
import views.gdx.core.ToastSink;
import views.renderers.InGameRenderer;

// Sends in-game command output to the toast overlay instead of System.out.
//
// Every in-game Command finishes by handing a Result to an InGameRenderer. The terminal build prints
// it; on the lawn there is no console to print to, so the same Result becomes a toast. Nothing else
// changes: the Command, the rule it enforced and the sentence it produced are identical, which is why
// "not enough sun" reads the same whether it was typed or clicked.
public final class GdxInGameRenderer implements InGameRenderer {

    private final ToastSink toasts;
    private final java.util.function.Consumer<String> listener;

    public GdxInGameRenderer(ToastSink toasts) {
        this(toasts, null);
    }

    // A second consumer of the same stream, for effects that are triggered by what the model SAYS.
    //
    // This exists because GameEngine drains the model's events itself, mid-tick, and renders them
    // through here -- so by the time a screen calls session.drainEvents() the queue is already empty.
    // Anything watching for a particular sentence (the explosion effect watching for "... detonates
    // at (x, y)!") has to tap it at this point or it never sees it at all.
    public GdxInGameRenderer(ToastSink toasts, java.util.function.Consumer<String> listener) {
        this.toasts = toasts;
        this.listener = listener;
    }

    @Override
    public void render(Result result) {
        if (result == null || result.message() == null || result.message().isBlank()) {
            return;
        }
        if (listener != null) {
            listener.accept(result.message());
        }
        toasts.show(result);
    }
}
