package views.renderers.MenuRenderer;

// Profile edits. Each method takes the outcome plus the validator's own refusal text, so the reason a
// change was rejected comes from the model rather than being reinvented per front end.
public interface ProfileMenuRenderer {
    void changeUsername(boolean success, String err);

    void changeNickname(boolean success, String err);

    void changePassword(boolean success, String err);

    void changeEmail(boolean success, String err);

    void showInfo(String output);
}
