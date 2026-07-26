package models.user;

import utils.Constants;

public class User {
    private String username;
    private String nickname;
    private String email;
    private Gender gender;
    private String hashPassword;
    private int securityQuestionIndex;
    private String securityAnswerHash;
    private Profile profile;   // Each user has their own profile
    private boolean stayLoggedIn;

    public User(String username, String nickname, String email, Gender gender,
                String hashPassword, int securityQuestionIndex, String securityAnswerHash) {
        this.username = username;
        this.nickname = nickname;
        this.email = email;
        this.gender = gender;
        this.hashPassword = hashPassword;
        this.securityQuestionIndex = securityQuestionIndex;
        this.securityAnswerHash = securityAnswerHash;
        this.profile = new Profile();
        this.stayLoggedIn = false;
    }

    public String getUsername() {
        return username;
    }

    public void changeUsername(String username) {
        this.username = username;
    }

    public String getNickname() {
        return nickname;
    }

    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getEmail() {
        return email;
    }

    public void changeEmail(String email) {
        this.email = email;
    }

    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }

    public String getHashPassword() {
        return hashPassword;
    }

    public void changePassword(String newPasswordHash) {
        this.hashPassword = newPasswordHash;
    }

    public int getSecurityQuestionIndex() {
        return securityQuestionIndex;
    }

    public void setSecurityQuestionIndex(int securityQuestionIndex) {
        this.securityQuestionIndex = securityQuestionIndex;
    }

    public String getSecurityAnswerHash() {
        return securityAnswerHash;
    }

    public void setSecurityAnswerHash(String securityAnswerHash) {
        this.securityAnswerHash = securityAnswerHash;
    }

    public Profile getProfile() {
        return profile;
    }

    public void setProfile(Profile profile) {
        this.profile = profile;
    }

    public boolean isStayLoggedIn() {
        return stayLoggedIn;
    }

    public void setStayLoggedIn(boolean stayLoggedIn) {
        this.stayLoggedIn = stayLoggedIn;
    }

    // Clamped rather than indexed raw: a hand-edited or older save file can carry an index outside the
    // question list, and an ArrayIndexOutOfBounds here would take down the whole recovery flow.
    public String getSecurityQuestion() {
        int index = Math.max(0, Math.min(securityQuestionIndex, Constants.SECURITY_QUESTIONS.length - 1));
        return Constants.SECURITY_QUESTIONS[index];
    }
}
