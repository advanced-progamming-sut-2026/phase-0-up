package views.net;

import com.badlogic.gdx.Gdx;
import net.Envelope;
import net.PacketType;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

// Where server pushes go on the client.
//
// ## Why not just NetClient.setPushListener
//
// Because there is more than one thing that wants them. The lobby needs challenge invites, the match
// screen needs snapshots and reactions, and both exist at different times. A single listener means the
// last screen to install one silently steals every packet from the previous -- and the symptom is a
// feature that simply stops working, with nothing logged and no exception anywhere.
//
// So this is a registry keyed by packet type, the same shape GameServer uses for the same reason. A
// screen claims what it cares about and RELEASES it when it closes; anything unclaimed is logged
// rather than dropped in silence, because an unhandled push is always either a missing screen or a
// forgotten registration.
//
// ## Everything is posted to the render thread
//
// Pushes arrive on the connection's reader thread. Touching Scene2D from there -- adding an actor,
// setting a label, showing a dialog -- corrupts the frame that is being drawn at that instant, and it
// does so intermittently, which is the worst way to find out. Gdx.app.postRunnable defers the handler
// to the top of the next frame, so a handler can do whatever it likes to the UI.
public final class PushRouter {

    private final Map<PacketType, Consumer<Envelope>> handlers = new EnumMap<>(PacketType.class);

    // Whether to hop threads. Off in tests, which have no LibGDX application to post into and which
    // want the handler to have run by the time the call returns.
    private final boolean postToRenderThread;

    public PushRouter() {
        this(true);
    }

    public PushRouter(boolean postToRenderThread) {
        this.postToRenderThread = postToRenderThread;
    }

    // Claim a packet type. Replacing an existing claim is allowed -- a screen reopening legitimately
    // re-registers -- but it is logged, because the common cause is two screens both wanting it and
    // one of them being about to stop working.
    public void on(PacketType type, Consumer<Envelope> handler) {
        if (type == null || handler == null) {
            return;
        }
        if (handlers.put(type, handler) != null) {
            log(type + " was claimed twice; the newer handler wins");
        }
    }

    // Release a claim. A screen MUST do this when it closes, or its handler keeps running against
    // actors that have been disposed.
    public void off(PacketType... types) {
        for (PacketType type : types) {
            handlers.remove(type);
        }
    }

    public void clear() {
        handlers.clear();
    }

    // Called by NetClient on the reader thread.
    public void accept(Envelope envelope) {
        Consumer<Envelope> handler = handlers.get(envelope.type());
        if (handler == null) {
            // Not silent. An unclaimed push is a screen that forgot to register, and the alternative
            // is a feature that quietly does nothing.
            log("unhandled push: " + envelope.type().tag());
            return;
        }
        if (!postToRenderThread) {
            deliver(envelope, handler);
            return;
        }
        Gdx.app.postRunnable(() -> deliver(envelope, handler));
    }

    private void deliver(Envelope envelope, Consumer<Envelope> handler) {
        try {
            handler.accept(envelope);
        } catch (RuntimeException e) {
            // A handler that throws must not take the render loop or the reader thread with it.
            log("push handler threw on " + envelope.type().tag() + ": " + e);
        }
    }

    private void log(String message) {
        if (Gdx.app != null) {
            Gdx.app.log("PushRouter", message);
        } else {
            System.out.println("[push] " + message);
        }
    }
}
