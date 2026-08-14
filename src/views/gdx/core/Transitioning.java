package views.gdx.core;

// A screen that would like to animate itself out before being replaced.
//
// ScreenManager swaps screens the moment the model's menu changes, which is correct but abrupt. A
// screen that implements this gets told first, and the swap waits for it. Anything that does not --
// GameScreen, the sprite harness -- is swapped immediately, exactly as before, so the transition is
// opt-in rather than something every screen has to know about.
public interface Transitioning {

    // Play the exit animation, then call whenDone.
    //
    // The contract is that whenDone runs EXACTLY once and always, even if the animation is cut short:
    // ScreenManager holds the pending swap until it fires, so a screen that never calls it would strand
    // the menu on the outgoing screen forever.
    void playExit(Runnable whenDone);
}
