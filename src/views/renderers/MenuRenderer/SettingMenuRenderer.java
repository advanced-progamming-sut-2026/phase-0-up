package views.renderers.MenuRenderer;

public class SettingMenuRenderer {
    public void changeDL(boolean success , int newDL){
        if(success){
            System.out.println("Difficulty level changed to : " + newDL);
        }
        else {
            System.out.println(newDL + " isn't a valid DL");
        }
    }
}
