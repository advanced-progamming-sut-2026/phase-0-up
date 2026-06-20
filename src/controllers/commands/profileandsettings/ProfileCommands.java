package controllers.commands.profileandsettings;

import controllers.commands.Command;
import models.user.User;
import utils.validation.EmailValidator;
import utils.validation.NicknameValidator;
import utils.validation.PasswordValidator;
import utils.validation.UsernameValidator;
import views.renderers.MenuRenderer.ProfileMenuRenderer;

public class ProfileCommands implements Command {
    private User user;
    private EditAction action;
    private String newValue;
    private String oldPassword;

    public ProfileCommands(User user, EditAction action, String newValue, String oldPassword) {
        this.user = user;
        this.action = action;
        this.newValue = newValue;
        this.oldPassword = oldPassword;
    }

    @Override
    public void execute() {
        ProfileMenuRenderer renderer = new ProfileMenuRenderer();
        switch (action){
            case USERNAME -> {
                if(new UsernameValidator().validate(newValue)){
                    if(user.getUsername().equals(newValue)){
                        renderer.changeUsername(false , "new username can't be the old one"); return;}
                    user.changeUsername(newValue);
                    renderer.changeUsername(true , null); return;
                } else {
                    renderer.changeUsername(false , "new username isn't valid"); return;}
            }
            case NICKNAME -> {
                if(new NicknameValidator().validate(newValue)){
                    if(user.getNickname().equals(newValue)){
                        renderer.changeNickname(false , "new nickname can't be the old one"); return;}
                    user.changeNickname(newValue);
                    renderer.changeNickname(true , null); return;
                } else {
                    renderer.changeNickname(false , "new nickname isn't valid"); return;}
            }
            case EMAIL -> {
                if(new EmailValidator().validate(newValue)){
                    if(user.getEmail().equals(newValue)){
                        renderer.changeEmail(false , "new email can't be the old one"); return;}
                    user.changeEmail(newValue);
                    renderer.changeEmail(true , null); return;
                } else {
                    renderer.changeEmail(false , "new email isn't valid"); return;}
            }
            case PASSWORD -> {
                if(new PasswordValidator().validate(newValue) && oldPassword.equals(user.getPassword())){
                    if(user.getPassword().equals(newValue)){
                        renderer.changePassword(false , "new password can't be the old one"); return;}
                    user.changePassword(newValue);
                    renderer.changePassword(true , null); return;
                } else {
                    if(!oldPassword.equals(user.getPassword())){
                        renderer.changePassword(false , "your old password isn't correct"); return;
                    } else {
                        renderer.changePassword(false , "new password isn't valid"); return;}
                }
            }
        }
    }
}
