package utils.storage;

import utils.Constants;

// The first step of password recovery: which security question this account was set up with.
//
// Carries the question INDEX rather than a User, and that is the point. ForgetPasswordCommand used to
// fetch the whole account to read one field off it, which over a network would mean handing an
// anonymous caller the very password hash they are trying to recover. Everything a front end needs to
// show the question is here, and nothing else is.
//
// The index is clamped on the way out for the same reason User.getSecurityQuestion clamps it: a
// hand-edited or older save can carry an index outside the question list, and an out-of-bounds read
// here would take down the whole recovery flow.
public record RecoveryStart(boolean ok, String message, int securityQuestionIndex) {

    public static RecoveryStart refused(String message) {
        return new RecoveryStart(false, message, -1);
    }

    public static RecoveryStart of(int securityQuestionIndex) {
        return new RecoveryStart(true, null, securityQuestionIndex);
    }

    public String question() {
        int index = Math.max(0, Math.min(securityQuestionIndex,
                Constants.SECURITY_QUESTIONS.length - 1));
        return Constants.SECURITY_QUESTIONS[index];
    }
}
