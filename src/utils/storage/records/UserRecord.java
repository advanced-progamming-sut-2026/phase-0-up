package utils.storage.records;

import models.user.Gender;
import models.user.User;

// Plain-data snapshot of a User for persistence (identity + credentials + a ProfileRecord). Field
// names match User so older save files load unchanged. Converting through this record is what keeps
// the save file free of any live game object.
public class UserRecord {
    private String username;
    private String nickname;
    private String email;
    private Gender gender;
    private String hashPassword;
    private int securityQuestionIndex;
    private String securityAnswerHash;
    private boolean stayLoggedIn;
    private ProfileRecord profile;

    public static UserRecord from(User u) {
        UserRecord r = new UserRecord();
        r.username = u.getUsername();
        r.nickname = u.getNickname();
        r.email = u.getEmail();
        r.gender = u.getGender();
        r.hashPassword = u.getHashPassword();
        r.securityQuestionIndex = u.getSecurityQuestionIndex();
        r.securityAnswerHash = u.getSecurityAnswerHash();
        r.stayLoggedIn = u.isStayLoggedIn();
        r.profile = u.getProfile() != null ? ProfileRecord.from(u.getProfile()) : null;
        return r;
    }

    public User toUser() {
        User u = new User(username, nickname, email, gender, hashPassword,
                securityQuestionIndex, securityAnswerHash);
        u.setStayLoggedIn(stayLoggedIn);
        if (profile != null) {
            u.setProfile(profile.toProfile());
        }
        return u;
    }

    public String getUsername() {
        return username;
    }
}
