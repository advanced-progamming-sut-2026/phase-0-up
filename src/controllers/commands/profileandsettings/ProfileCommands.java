package controllers.commands.profileandsettings;

import controllers.commands.Command;
import models.user.User;
import utils.Result;
import utils.storage.DatabaseManager;
import utils.storage.PasswordHasher;
import utils.validation.*;
import views.renderers.MenuRenderer.ProfileMenuRenderer;

public class ProfileCommands implements Command {
    private final User user;
    private final EditAction action;
    private final String newValue;
    private final String oldPassword;
    private final ProfileMenuRenderer renderer;

    public ProfileCommands(User user, EditAction action, String newValue, String oldPassword, ProfileMenuRenderer renderer) {
        this.user = user;
        this.action = action;
        this.newValue = newValue;
        this.oldPassword = oldPassword;
        this.renderer = renderer;
    }

    @Override
    public void execute() {
        if (user == null) {
            renderer.changeUsername(false, "You need to be logged in to edit a profile.");
            return;
        }
        switch (action) {
            case USERNAME -> handleUsernameChange();
            case NICKNAME -> handleNicknameChange();
            case EMAIL    -> handleEmailChange();
            case PASSWORD -> handlePasswordChange();
        }
        DatabaseManager.getInstance().saveAll();
    }

    private void handleUsernameChange() {
        String candidate = newValue == null ? null : newValue.trim();

        // Checked before validation so "that's already yours" never surfaces as "already taken".
        if (candidate != null && candidate.equals(user.getUsername())) {
            renderer.changeUsername(false, "'" + candidate + "' is already your username!");
            return;
        }
        // Validated against everyone *except* this account, so re-casing your own name is allowed
        // while another player's name (in any casing) is still rejected as taken.
        Result validation = new UsernameValidator(user.getUsername()).validate(candidate);
        if (!validation.success()) {
            renderer.changeUsername(false, validation.message());
            return;
        }
        // The database keys accounts by username, so the rename has to go through the database --
        // mutating the User alone left the account filed under its old name, where the new name found
        // nobody and the old name still logged in.
        if (!DatabaseManager.getInstance().renameUser(user.getUsername(), candidate)) {
            renderer.changeUsername(false, "Couldn't rename you to '" + candidate
                    + "' -- another gardener claimed it just now.");
            return;
        }
        renderer.changeUsername(true, null);
    }

    private void handleNicknameChange() {
        Result validation = new NicknameValidator().validate(newValue);
        if (!validation.success()) {
            renderer.changeNickname(false, validation.message());
            return;
        }
        String candidate = newValue.trim();
        if (candidate.equals(user.getNickname())) {
            renderer.changeNickname(false, "'" + candidate + "' is already your nickname!");
            return;
        }
        user.changeNickname(candidate);
        renderer.changeNickname(true, null);
    }

    private void handleEmailChange() {
        Result validation = new EmailValidator().validate(newValue);
        if (!validation.success()) {
            renderer.changeEmail(false, validation.message());
            return;
        }
        String candidate = newValue.trim();
        // Email is compared without regard to case: mailboxes are matched that way here (see
        // ForgetPasswordCommand), so "Me@x.com" is not a different address from "me@x.com".
        if (candidate.equalsIgnoreCase(user.getEmail())) {
            renderer.changeEmail(false, "'" + candidate + "' is already your email!");
            return;
        }
        user.changeEmail(candidate);
        renderer.changeEmail(true, null);
    }

    private void handlePasswordChange() {
        Result validation = new PasswordValidator().validate(newValue);
        if (!validation.success()) {
            renderer.changePassword(false, validation.message());
            return;
        }
        if (!PasswordHasher.matches(oldPassword, user.getHashPassword())) {
            renderer.changePassword(false, "Your old password isn't correct");
            return;
        }
        if (PasswordHasher.matches(newValue, user.getHashPassword())) {
            renderer.changePassword(false, "New password is the one you already have!");
            return;
        }

        String hashedPassword = PasswordHasher.hash(newValue);
        user.changePassword(hashedPassword);
        renderer.changePassword(true, null);
    }
}
