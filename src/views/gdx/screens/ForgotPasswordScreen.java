package views.gdx.screens;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import controllers.commands.authentication.ForgetPasswordCommand;
import utils.storage.DatabaseManager;
import utils.storage.RecoveryStart;
import views.gdx.core.GdxContext;
import views.gdx.ui.MenuStyles;

// Password recovery, in the two steps the flow actually has.
//
// It cannot be one form: the player has to see their security question before they can answer it, and
// which question it is depends on the account. So step one identifies the account and reveals the
// question, step two collects the answer and the new password and submits both at once through the
// non-interactive constructor from T3.5.
//
// Reading the account here to show the question is a read of model state, not a rule: the Command
// still re-finds the user, still re-checks the email, and still verifies the answer. Getting this
// screen's lookup wrong can only mean showing the wrong question, never letting the wrong person in.
public final class ForgotPasswordScreen extends MenuScreen {

    private TextField username;
    private TextField email;
    private Label questionLabel;
    private TextField answer;
    private TextField newPassword;
    private Table step2;
    private TextButton findButton;

    public ForgotPasswordScreen(GdxContext context) {
        super(context);
    }

    @Override
    protected void build(Table root) {
        username = MenuStyles.field(skin, "username");
        email = MenuStyles.field(skin, "email on the account");
        questionLabel = MenuStyles.label(skin, "", MenuStyles.TEXT);
        questionLabel.setWrap(true);
        answer = MenuStyles.field(skin, "your answer");
        newPassword = MenuStyles.secret(skin, "new password");

        Table panel = MenuPanel.build(skin, "Forgot Password");
        panel.add(username).width(360f).height(48f).padBottom(8f).row();
        panel.add(email).width(360f).height(48f).padBottom(12f).row();

        findButton = MenuStyles.button(skin, "Find My Account", MenuStyles.BUTTON_BROWN);
        findButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                findAccount();
            }
        });
        panel.add(findButton).width(260f).height(56f).padBottom(10f).row();

        step2 = buildStep2();
        step2.setVisible(false);
        panel.add(step2).width(380f).row();

        panel.add(backButton()).width(200f).height(52f).padTop(12f).row();

        root.setFillParent(true);
        root.add(panel);
    }

    private Table buildStep2() {
        Table table = new Table();
        table.add(questionLabel).width(360f).padBottom(8f).row();
        table.add(answer).width(360f).height(48f).padBottom(8f).row();
        table.add(newPassword).width(360f).height(48f).padBottom(12f).row();

        TextButton reset = MenuStyles.button(skin, "Reset Password", MenuStyles.BUTTON_GREEN);
        reset.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                resetPassword();
            }
        });
        table.add(reset).width(240f).height(60f).row();
        return table;
    }

    // Reveal the question, or say why not. The email is checked here as well as in the Command so that
    // knowing a username alone does not disclose which question guards the account.
    private void findAccount() {
        String name = username.getText().trim();
        String mail = email.getText().trim();
        if (!MenuForms.filled(context, name, "username") || !MenuForms.filled(context, mail, "email")) {
            return;
        }

        // Asks the backend to look the account up and hand back only the question.
        //
        // This used to fetch the whole User and compare the email here, which quietly required the
        // storage layer to give an unauthenticated caller a complete account -- password hash and
        // security-answer hash included -- for any username typed into the box. beginRecovery does the
        // same two checks where the account actually lives and returns a question and nothing else.
        RecoveryStart start = DatabaseManager.getInstance().beginRecovery(name, mail);
        if (!start.ok()) {
            context.toasts().error("No account matches that username and email.");
            step2.setVisible(false);
            return;
        }

        questionLabel.setText(stripNumbering(start.question()));
        step2.setVisible(true);
        findButton.setText("Not you? Search again");
    }

    private void resetPassword() {
        String givenAnswer = answer.getText().trim();
        String password = newPassword.getText();
        if (!MenuForms.require(context, givenAnswer, "Answer your security question.")
                || !MenuForms.filled(context, password, "new password")) {
            return;
        }
        new ForgetPasswordCommand(username.getText().trim(), email.getText().trim(),
                givenAnswer, password, context.appSession(),
                context.renderers().loginMenu()).execute();
    }

    private String stripNumbering(String question) {
        return question == null ? "" : question.replaceFirst("^\\s*\\d+\\.\\s*", "");
    }

    private TextButton backButton() {
        TextButton button = MenuStyles.button(skin, "Back to Sign In", MenuStyles.BUTTON_BROWN);
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                goBack();
            }
        });
        return button;
    }

    // This screen is not a MenuType, so ScreenManager has nothing to sync back to -- it has to put the
    // login screen up itself.
    @Override
    protected void goBack() {
        context.screens().showDetached(new LoginScreen(context));
    }
}
