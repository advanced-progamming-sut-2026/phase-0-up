package models.user;

import java.util.Map;

public class User {
    private String username;
    private String nickname;
    private String email;
    private Gender gender;
    private String hashPassword;
    private int securityQuestionIndex;
    private String securityAnswerHash;
    private Profile profile;   //each user has its profile
    private boolean stayLoggedIn = false;

    public User(String username, String nickname, String email, Gender gender,
                String hashPassword, int securityQuestionIndex, String securityAnswerHash) {
        this.username = username;
        this.nickname = nickname;
        this.email = email;
        this.gender = gender;
        this.hashPassword = hashPassword;
        this.securityQuestionIndex = securityQuestionIndex;
        this.securityAnswerHash = securityAnswerHash;
        this.profile =  new Profile();
        this.stayLoggedIn = false;
    }


    public User(String username, String password, String nickname, String email, Gender gender, String hashPassword, Map<Integer, String> securityQuestions , Profile profile){}
    public String getUsername(){return null;}
    public void changeUsername(String username) {this.username = username;}
    public String getPassword(){return null;}
    public void changePassword(String newPassword){}
    public Profile getProfile(){return null;}
    public String getNickname() {return null;}
    public void changeNickname(String nickname){this.nickname = nickname;}
    public String getEmail() {return null;}
    public void changeEmail(String email){this.email = email;}
    public Gender getGender() {return null;}
    public String getHashPassword() {return null;}
    public Map<Integer, String> getSecurityQuestions() {return null;}
    public void setStayLoggedIn(boolean stayLoggedIn) {}
    public boolean isStayLoggedIn() {return false;}
}
