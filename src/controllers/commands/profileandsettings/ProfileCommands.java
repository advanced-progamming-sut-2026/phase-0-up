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
        switch (action) {
            case USERNAME -> handleUsernameChange();
            case NICKNAME -> handleNicknameChange();
            case EMAIL    -> handleEmailChange();
            case PASSWORD -> handlePasswordChange();
        }
        DatabaseManager.getInstance().saveAll();
    }

    private void handleUsernameChange() {
        Result validation = new UsernameValidator().validate(newValue);
        if (!validation.success()) {
            renderer.changeUsername(false, "New username isn't valid");
            return;
        }
        if (newValue.equals(user.getUsername())) {
            renderer.changeUsername(false, "New username can't be the old one");
            return;
        }
        user.changeUsername(newValue);
        renderer.changeUsername(true, null);
    }

    private void handleNicknameChange() {
        Result validation = new NicknameValidator().validate(newValue);
        if (!validation.success()) {
            renderer.changeNickname(false, "New nickname isn't valid");
            return;
        }
        if (newValue.equals(user.getNickname())) {
            renderer.changeNickname(false, "New nickname can't be the old one");
            return;
        }
        user.changeNickname(newValue);
        renderer.changeNickname(true, null);
    }

    private void handleEmailChange() {
        Result validation = new EmailValidator().validate(newValue);
        if (!validation.success()) {
            renderer.changeEmail(false, "New email isn't valid");
            return;
        }
        if (newValue.equals(user.getEmail())) {
            renderer.changeEmail(false, "New email can't be the old one");
            return;
        }
        user.changeEmail(newValue);
        renderer.changeEmail(true, null);
    }

    private void handlePasswordChange() {
        if (!new PasswordValidator().validate(newValue).success()) {
            renderer.changePassword(false, "New password isn't valid");
            return;
        }
        if (!PasswordHasher.matches(oldPassword, user.getHashPassword())) {
            renderer.changePassword(false, "Your old password isn't correct");
            return;
        }
        if (PasswordHasher.matches(newValue, user.getHashPassword())) {
            renderer.changePassword(false, "New password can't be the old one");
            return;
        }

        String hashedPassword = PasswordHasher.hash(newValue);
        user.changePassword(hashedPassword);
        renderer.changePassword(true, null);
    }
}
