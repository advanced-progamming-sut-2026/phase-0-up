package views.renderers.MenuRenderer;

import models.greenhouse.Pot;
import utils.Result;
import views.OutputHandler;

public class GreenhouseRenderer {
    public void showGreenhouse(Pot[] pots){}
    public void plantPot(Result result){
        OutputHandler.showMessage(result.message());
    }
}
