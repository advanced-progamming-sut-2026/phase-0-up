package views.renderers.MenuRenderer;

import controllers.engine.InputRouter;
import controllers.engine.MenuType;
import utils.Result;
import views.OutputHandler;

public class AllMenuRenderer {


    public void showCurrentMenu(MenuType menu) {
        OutputHandler.showMessage("Current Menu: " + menu.getMenuName());
    }
    public void enterMenu(Result result){
        OutputHandler.showMessage(result.message());
    }
    public void menuExit(String destination){
        OutputHandler.showMessage(String.format("returned to %s menu",destination));
    }

}
