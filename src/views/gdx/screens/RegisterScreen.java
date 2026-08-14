package views.gdx.screens;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import controllers.commands.authentication.RegisterCommand;
import controllers.engine.MenuType;
import utils.Constants;
import views.gdx.core.GdxContext;
import views.gdx.ui.Cycler;
import views.gdx.ui.MenuStyles;

// Creating an account.
//
// The only screen so far that does NOT post a command string. RegisterCommand's string form prompts on
// stdin for the security question, which would freeze the render thread -- so this uses the
// non-interactive constructor added in T3.5 and hands over the completed form instead. Every validator
// still runs inside the Command; nothing about the rules is re-implemented here.
public final class RegisterScreen extends MenuScreen {

    private static final String[] GENDERS = {"Male", "Female"};

    private TextField username;
    private TextField password;
    private TextField passwordConfirm;
    private TextField nickname;
    private TextField email;
    private Cycler gender;
    private Cycler question;
    private TextField answer;

    public RegisterScreen(GdxContext context) {
        super(context);
    }

    @Override
    protected void build(Table root) {
        username = MenuStyles.field(skin, "username");
        password = MenuStyles.secret(skin, "password");
        passwordConfirm = MenuStyles.secret(skin, "confirm password");
        nickname = MenuStyles.field(skin, "nickname");
        email = MenuStyles.field(skin, "email");
        gender = new Cycler(skin, GENDERS, 0);
        question = new Cycler(skin, questions(), 0);
        answer = MenuStyles.field(skin, "your answer");

        Table panel = MenuPanel.build(skin, "Join the Lawn");
        addField(panel, username);
        addField(panel, password);
        addField(panel, passwordConfirm);
        addField(panel, nickname);
        addField(panel, email);
        // Captioned, because a cycler showing "Male" with no label says nothing about what is being
        // chosen -- and the security question one is worse, since the question reads like a prompt for
        // the field above it rather than a choice.
        panel.add(caption("I am")).padBottom(1f).row();
        panel.add(gender).width(400f).padBottom(6f).row();
        panel.add(caption("Security question")).padBottom(1f).row();
        panel.add(question).width(400f).padBottom(4f).row();
        addField(panel, answer);

        panel.add(signUpButton()).width(220f).height(56f).padTop(4f).padBottom(6f).row();
        panel.add(backButton()).width(200f).height(44f).row();

        root.setFillParent(true);
        root.add(panel);
    }

    private void addField(Table panel, TextField field) {
        panel.add(field).width(360f).height(42f).padBottom(6f).row();
    }

    private com.badlogic.gdx.scenes.scene2d.ui.Label caption(String text) {
        return MenuStyles.label(skin, text, MenuStyles.TEXT);
    }

    // The stored questions carry their own numbering ("1. What was..."), which is a prompt affordance:
    // it exists so the player can type "-q 3". A cycler shows one at a time, so the number is noise.
    private String[] questions() {
        String[] shown = new String[Constants.SECURITY_QUESTIONS.length];
        for (int i = 0; i < shown.length; i++) {
            shown[i] = Constants.SECURITY_QUESTIONS[i].replaceFirst("^\\s*\\d+\\.\\s*", "");
        }
        return shown;
    }

    private TextButton signUpButton() {
        TextButton button = MenuStyles.button(skin, "Sign Up", MenuStyles.BUTTON_GREEN);
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                submit();
            }
        });
        return button;
    }

    private TextButton backButton() {
        TextButton button = MenuStyles.button(skin, "I have an account", MenuStyles.BUTTON_BROWN);
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                commands.enter(MenuType.LOGIN_MENU.getMenuName());
            }
        });
        return button;
    }

    private void submit() {
        if (!fieldsAreUsable()) {
            return;
        }
        // questionNumber is 1-based, matching what the terminal asks the player to type; the cycler is
        // an array index.
        RegisterCommand.Form form = new RegisterCommand.Form(
                username.getText().trim(),
                password.getText(),
                passwordConfirm.getText(),
                nickname.getText().trim(),
                email.getText().trim(),
                gender.selected().toLowerCase(java.util.Locale.ROOT),
                question.selectedIndex() + 1,
                answer.getText().trim());

        new RegisterCommand(form, context.renderers().signUpMenu(),
                context.appSession(), context.renderers().allMenu()).execute();
    }

    // Only the whitespace guard: every other rule -- length, character set, "passwords do not match",
    // "username already exists" -- belongs to the Command and is checked there for both builds.
    //
    // The security answer is the exception that is allowed spaces: LoginMenuRegex.ANSWER_SECURITY
    // accepts a quoted phrase, and "my first dog" is a perfectly ordinary answer. It is never pasted
    // into a command string on this path, so nothing can split it.
    private boolean fieldsAreUsable() {
        return MenuForms.filled(context, username.getText().trim(), "username")
                && MenuForms.filled(context, password.getText(), "password")
                && MenuForms.filled(context, nickname.getText().trim(), "nickname")
                && MenuForms.filled(context, email.getText().trim(), "email")
                && MenuForms.require(context, answer.getText().trim(),
                        "Answer your security question -- you'll need it if you forget your password.");
    }

    @Override
    protected void goBack() {
        commands.enter(MenuType.LOGIN_MENU.getMenuName());
    }
}
