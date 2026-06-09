package controllers.commands.authentication;

import controllers.auth.SessionManager;
import controllers.commands.Command;

public class ForgetPasswordCommand implements Command {
    private String Username;
    private String email;
    private SessionManager sessionManager;

    @Override
    public void execute() {

    }
}
