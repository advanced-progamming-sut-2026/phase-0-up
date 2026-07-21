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
        OutputHandler.showMessage(String.format("Still sprouting -- give it another %s.",
                pot.getRemainingTimeFormatted()));
    }

    public void invalidPotState(PotState potState){
        OutputHandler.showMessage(potState == PotState.EMPTY
                ? "That pot is empty. Pop something in it first!"
                : "That pot is still locked.");
    }

    public void collect(Result result){
        OutputHandler.showMessage(result.message());
    }

    public void grow(Result result){
        OutputHandler.showMessage(result.message());
    }

    public void enterShop() {OutputHandler.showMessage("You are now in Shop! :)");}
}
