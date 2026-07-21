package views.renderers.MenuRenderer;

public class ProfileMenuRenderer {
    public void changeUsername(boolean success , String err){
        if(success){
            System.out.println("New username, same great gardener. Done!");
        } else {
            System.out.println(err);
        }
    }
    public void changeNickname(boolean success , String err) {
            if (success) {
                System.out.println("Nickname updated -- the neighbours will be so impressed.");
            } else {
                System.out.println(err);
            }
    }
    public void changePassword(boolean success , String err){
        if(success){
            System.out.println("Password changed. Locked up tighter than a Wall-nut.");
        } else {
            System.out.println(err);
        }
    }
    public void changeEmail(boolean success , String err){
        if(success){
            System.out.println("Email updated. Crazy Dave will be in touch.");
        } else {
            System.out.println(err);
        }
    }
    public void showInfo(String output){
        System.out.println(output);
    }
}
