package views.gdx.renderers;

import models.greenhouse.Pot;
import models.greenhouse.PotState;
import utils.Result;
import views.gdx.core.ToastSink;
import views.renderers.MenuRenderer.GreenhouseRenderer;

// The greenhouse. GreenhouseScreen (T6.1/T6.2) draws the pots and their timers; what stays here are
// the outcomes of acting on one.
public final class GdxGreenhouseRenderer implements GreenhouseRenderer {

    private final ToastSink toasts;

    public GdxGreenhouseRenderer(ToastSink toasts) {
        this.toasts = toasts;
    }

    @Override
    public void showGreenhouse(String status) {
        // The screen is the greenhouse.
    }

    @Override
    public void plantPot(Result result) {
        toasts.show(result);
    }

    @Override
    public void potNotReadyYet(Pot pot) {
        toasts.error(String.format("Still sprouting -- give it another %s.",
                pot.getRemainingTimeFormatted()));
    }

    @Override
    public void invalidPotState(PotState potState) {
        toasts.error(potState == PotState.EMPTY
                ? "That pot is empty. Pop something in it first!"
                : "That pot is still locked.");
    }

    @Override
    public void collect(Result result) {
        toasts.show(result);
    }

    @Override
    public void grow(Result result) {
        toasts.show(result);
    }

    @Override
    public void enterShop() {
        toasts.info("You are now in Shop! :)");
    }
}
