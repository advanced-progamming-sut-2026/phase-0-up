package views.renderers.MenuRenderer;

public class ProfileMenuRenderer {
    public void changeUsername(boolean success , String err){
        if(success){
            System.out.println("your username changed successfully!");
        } else {
            System.out.println(err);
        }
    }
    public void changeNickname(boolean success , String err) {
            if (success) {
                System.out.println("your nickname changed successfully!");
            } else {
                System.out.println(err);
            }
    }
    public void changePassword(boolean success , String err){
        if(success){
            System.out.println("your password changed successfully!");
        } else {
            System.out.println(err);
        }
    }
    public void changeEmail(boolean success , String err){
        if(success){
            System.out.println("your email changed successfully!");
        } else {
            System.out.println(err);
        }
    }
    public void showInfo(String output){
        System.out.println(output);
    }
}
