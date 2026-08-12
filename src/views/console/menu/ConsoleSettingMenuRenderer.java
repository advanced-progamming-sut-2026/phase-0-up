package views.console.menu;

import views.renderers.MenuRenderer.SettingMenuRenderer;

public class ConsoleSettingMenuRenderer implements SettingMenuRenderer {
    @Override
    public void changeDL(boolean success , int newDL){
        if(success){
            System.out.println("Difficulty set to " + newDL + ". The zombies have been notified.");
        }
        else {
            System.out.println(newDL + " isn't a difficulty level anyone recognises.");
        }
    }
}
