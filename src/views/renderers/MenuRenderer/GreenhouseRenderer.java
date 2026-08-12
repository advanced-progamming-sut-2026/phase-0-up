package views.renderers.MenuRenderer;

import models.greenhouse.Pot;
import models.greenhouse.PotState;
import utils.Result;

// The greenhouse: pots, what is growing in them, and what you may do about it.
public interface GreenhouseRenderer {
    void showGreenhouse(String status);

    void plantPot(Result result);

    void potNotReadyYet(Pot pot);

    void invalidPotState(PotState potState);

    void collect(Result result);

    void grow(Result result);

    void enterShop();
}
