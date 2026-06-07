package controllers.auth;

import models.user.User;

import java.util.Map;

public class SessionManager {
    private User currentUser;

    public void register(){};
    public boolean login(String username , String password , boolean stayLoggedIn){return false;}
    public Map<Integer , String> forgetPassword(String username , String Email){return null;}
    public boolean answerSecurityQuestions(Map<Integer , String> securityQuestions){return false;}
    public void logout(){};
    public void resetPassword(String newPassword){};
}
