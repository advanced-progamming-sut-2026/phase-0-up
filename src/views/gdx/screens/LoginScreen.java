package views.gdx.screens;

import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import views.gdx.core.GdxContext;
import views.gdx.ui.MenuStyles;

// Signing in.
//
// The button builds the same "login -u ... -p ..." the terminal takes and posts it through the router,
// so the account rules, the "stay signed in" flag and every refusal sentence are the model's, not this
// screen's. What the screen adds is the one thing a prompt gets for free and a form does not: values
// are checked for whitespace before they are pasted into a command string.
public final class LoginScreen extends MenuScreen {

    private TextField username;
    private TextField password;
    private TextButton stayLoggedIn;
    private boolean stayChecked;

    public LoginScreen(GdxContext context) {
        super(context);
    }

    @Override
    protected void build(Table root) {
        username = MenuStyles.field(skin, "username");
        password = MenuStyles.secret(skin, "password");
        stayLoggedIn = MenuStyles.toggle(skin, "Stay signed in", false, on -> stayChecked = on);

        Table panel = MenuPanel.build(skin, "Welcome Back");
        panel.add(username).width(340f).height(52f).padBottom(10f).row();
        panel.add(password).width(340f).height(52f).padBottom(10f).row();
        panel.add(stayLoggedIn).width(260f).height(46f).padBottom(14f).row();

        TextButton signIn = MenuStyles.button(skin, "Let's Go", MenuStyles.BUTTON_GREEN);
        signIn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                submit();
            }
        });
        panel.add(signIn).width(220f).height(64f).padBottom(12f).row();

        Table links = new Table();
        links.add(link("Forgot password?", true)).padRight(20f);
        links.add(link("Create an account", false));
        panel.add(links).row();

        root.setFillParent(true);
        root.add(panel);
    }

    // The two side routes off this screen. "Forgot password" is a sibling view of the login menu rather
    // than a menu of its own -- the model has no MenuType for it -- so it is shown detached.
    private TextButton link(String text, boolean forgotPassword) {
        TextButton button = MenuStyles.button(skin, text, MenuStyles.BUTTON_BROWN);
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                if (forgotPassword) {
                    context.screens().showDetached(new ForgotPasswordScreen(context));
                } else {
                    goBack();
                }
            }
        });
        return button;
    }

    private void submit() {
        String user = username.getText().trim();
        String pass = password.getText();
        if (!MenuForms.require(context, user, "Enter your username.")
                || !MenuForms.require(context, pass, "Enter your password.")) {
            return;
        }
        // The command grammar splits on whitespace, so a value containing a space would silently
        // become a different command. Refusing here says what is wrong; letting it through would
        // produce "invalid command", which tells the player nothing.
        if (!MenuForms.noSpaces(context, user, "Usernames have no spaces in them.")
                || !MenuForms.noSpaces(context, pass, "Passwords can't contain spaces.")) {
            return;
        }
        commands.submit("login -u " + user + " -p " + pass
                + (stayChecked ? " -stay-logged-in" : ""));
    }

    // Back from the login menu is the sign-up menu -- and it is reached by EXITING, not by entering.
    //
    // Same trap as the plants menu: EnterMenuCommand's edge list allows login -> main and nothing else,
    // so "menu enter signup" was refused and both Escape and the "Create an account" link did nothing.
    // ExitMenuCommand is the one that maps login back to sign-up, which is also why the link posts an
    // exit rather than an enter: that IS the model's only route between the two.
    @Override
    protected void goBack() {
        commands.back();
    }
}
