package controllers.commands.authentication;

import controllers.auth.SessionManager;
import controllers.commands.Command;

public class LoginCommand implements Command {
    private String username;
    private String password;
    private boolean stayLoggedIn;
    private SessionManager sessionManager;

    @Override
    public void execute() {
    }
}
