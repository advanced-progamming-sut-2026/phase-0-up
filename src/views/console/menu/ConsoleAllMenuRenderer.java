package views.console.menu;

import controllers.engine.MenuType;
import utils.Result;
import views.OutputHandler;
import views.renderers.MenuRenderer.AllMenuRenderer;

public class ConsoleAllMenuRenderer implements AllMenuRenderer {

    @Override
    public void showCurrentMenu(MenuType menu) {
        OutputHandler.showMessage("You are in the " + menu.getMenuName() + " menu.");
    }

    @Override
    public void enterMenu(Result result){
        OutputHandler.showMessage(result.message());
    }

    @Override
    public void menuExit(String destination){
        OutputHandler.showMessage(String.format("Back to the %s menu.", destination));
    }

    @Override
    public void applicationExit(){
        OutputHandler.showMessage("Progress saved. Thanks for defending the lawn -- see you next time!");
    }

    @Override
    public void invalidCommand(){
        OutputHandler.showMessage("Invalid command! Try \"menu show current\" to see where "
                + "you are, or check GUIDE.md.");
    }
}
