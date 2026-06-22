package views.renderers.MenuRenderer;

import models.greenhouse.Pot;
import models.greenhouse.PotState;
import utils.Result;
import views.OutputHandler;

public class GreenhouseRenderer {
    public void showGreenhouse(String status){
        OutputHandler.showMessage(status);
    }
    public void plantPot(Result result){
        OutputHandler.showMessage(result.message());
    }

    public void potNotReadyYet(Pot pot){
        OutputHandler.showMessage(String.format("This pot is not ready, remaining time : %s",
                pot.getRemainingTimeFormatted()));
    }

    public void invalidPotState(PotState potState){
        OutputHandler.showMessage(String.format("This pot is %s", potState == PotState.EMPTY ? "empty" : "locked"));
    }

    public void collect(Result result){
        OutputHandler.showMessage(result.message());
    }

    public void grow(Result result){
        OutputHandler.showMessage(result.message());
    }
}
