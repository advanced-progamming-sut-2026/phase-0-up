package views.renderers.MenuRenderer;

import controllers.engine.InputRouter;
import controllers.engine.MenuType;
import utils.Result;
import views.OutputHandler;

public class AllMenuRenderer {


    public void showCurrentMenu(MenuType menu) {
        OutputHandler.showMessage("You are in the " + menu.getMenuName() + " menu.");
    }

    public void enterMenu(Result result){
        OutputHandler.showMessage(result.message());
    }

    public void menuExit(String destination){
        OutputHandler.showMessage(String.format("Back to the %s menu.", destination));
    }

    public void invalidCommand(){
        OutputHandler.showMessage("That's not a command I know. Try \"menu show current\" to see where "
                + "you are, or check GUIDE.md.");
    }

}
