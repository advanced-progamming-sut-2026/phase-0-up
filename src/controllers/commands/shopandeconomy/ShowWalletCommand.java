package controllers.commands.shopandeconomy;

import controllers.commands.Command;
import models.user.User;

public class ShowWalletCommand implements Command {
    private Currency currency;
    private User currentUser;

    @Override
    public void execute() {}
}
