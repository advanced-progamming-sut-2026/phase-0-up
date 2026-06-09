package controllers.commands.authentication;

import controllers.auth.SessionManager;
import controllers.commands.Command;

public class RegisterCommand implements Command {
    private String username;
    private String password;
    private String passwordConfirm;
    private String nickname;
    private String email;
    private String gender;
    private SessionManager sessionManager;

    @Override
    public void execute() {

    }
}
