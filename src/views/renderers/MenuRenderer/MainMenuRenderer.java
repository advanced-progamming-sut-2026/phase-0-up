package views.renderers.MenuRenderer;

public class MainMenuRenderer {
    public void logOutRender(boolean success){
        if(success){
            System.out.println("Logged out successfully!  You are now in the SignUp Menu");
        }
        else {
            System.out.println("You are not Logged In!");
        }
    }
}
