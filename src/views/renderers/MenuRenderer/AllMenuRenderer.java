package views.renderers.MenuRenderer;

import controllers.engine.InputRouter;
import controllers.engine.MenuType;
import views.OutputHandler;

public class AllMenuRenderer {


    public void showCurrentMenu(){}
    public void enterMenu(){}
    public void menuExit(String destination){
        OutputHandler.showMessage(String.format("returned to %s menu",destination));
    }

}
