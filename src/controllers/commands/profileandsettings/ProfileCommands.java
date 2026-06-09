package controllers.commands.profileandsettings;

import controllers.commands.Command;
import models.user.Profile;

public class ProfileCommands implements Command {
    private Profile profile;
    private EditAction action;
    private String newValue;
    private String oldPassword;

    @Override
    public void execute() {}
}
