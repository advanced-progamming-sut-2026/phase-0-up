package views.console.menu;

import models.greenhouse.Pot;
import models.greenhouse.PotState;
import utils.Result;
import views.OutputHandler;
import views.renderers.MenuRenderer.GreenhouseRenderer;

public class ConsoleGreenhouseRenderer implements GreenhouseRenderer {
    @Override
    public void showGreenhouse(String status){
        OutputHandler.showMessage(status);
    }

    @Override
    public void plantPot(Result result){
        OutputHandler.showMessage(result.message());
    }

    @Override
    public void potNotReadyYet(Pot pot){
        OutputHandler.showMessage(String.format("Still sprouting -- give it another %s.",
                pot.getRemainingTimeFormatted()));
    }

    @Override
    public void invalidPotState(PotState potState){
        OutputHandler.showMessage(potState == PotState.EMPTY
                ? "That pot is empty. Pop something in it first!"
                : "That pot is still locked.");
    }

    @Override
    public void collect(Result result){
        OutputHandler.showMessage(result.message());
    }

    @Override
    public void grow(Result result){
        OutputHandler.showMessage(result.message());
    }

    @Override
    public void enterShop() {
        OutputHandler.showMessage("You are now in Shop! :)");
    }
}
