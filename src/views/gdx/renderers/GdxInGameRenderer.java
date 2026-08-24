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

    // What was said while nobody was listening.
    //
    // `GameEngine.init()` runs BEFORE GameScreen exists -- `newEngine` calls it in a static helper on
    // the real path, and DevBoot calls it before it hands the engine over -- and it deliberately drains
    // everything `onStart` queued so the mode's banner is not a tick late. Those sentences therefore go
    // out through the APP-LEVEL renderer, which has no listener, and the screen's fan-out never sees
    // them. Every consumer of that fan-out silently misses them; for most it does not matter, because
    // nothing detonates during setup. It matters completely for the one thing whose entire content is
    // the banner (see NpcDialogueBox), and it would matter for anything future that reacts to a mode's
    // opening statement.
    //
    // So a listener-less renderer keeps what it rendered, and the screen collects it once it has
    // somewhere to put it. Only recorded when there is NO listener: a message that had one has already
    // been delivered, and buffering it would deliver it twice.
    private static final int BACKLOG_LIMIT = 32;

    private final java.util.List<String> backlog = new java.util.ArrayList<>();

    // Takes the backlog and empties it, so a second screen cannot replay a first one's opening.
    public java.util.List<String> drainBacklog() {
        java.util.List<String> taken = new java.util.ArrayList<>(backlog);
        backlog.clear();
        return taken;
    }

    @Override
    public void render(Result result) {
        if (result == null || result.message() == null || result.message().isBlank()) {
            return;
        }
        if (listener != null) {
            listener.accept(result.message());
        } else if (backlog.size() < BACKLOG_LIMIT) {
            // Capped rather than a ring: what this is for is the handful of sentences a level opens
            // with, and quietly dropping the OLDEST of those would lose the banner -- the one message
            // it exists to carry.
            backlog.add(result.message());
        }
        toasts.show(result);
    }
}
