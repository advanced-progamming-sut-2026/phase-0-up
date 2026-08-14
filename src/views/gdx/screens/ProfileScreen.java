package views.gdx.screens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Scaling;
import models.user.Profile;
import models.user.User;
import views.gdx.core.GdxContext;
import views.gdx.ui.MenuStyles;

// The player's account.
//
// Two halves: what the profile currently is, and four edits that each post the command the terminal
// takes. A changed username is why the summary is re-read every frame rather than captured -- renaming
// re-keys the account in DatabaseManager, and a cached label would go on showing the old name until the
// screen was rebuilt.
//
// The figures come straight off the Profile getters rather than through ShowProfileCommand, whose
// output is one pre-formatted block built for stdout: useful to print, impossible to lay out.
public final class ProfileScreen extends MenuScreen {

    // Shipped icons, all already in the loaded skin.
    private static final String ICON_COINS = "image_ui_coins_stack_0";
    private static final String ICON_GEMS = "image_ui_gems_stack_1";
    private static final String ICON_STAR = "image_ui_generic_star_icon";
    // Games played gets the wave meter's zombie head. Nothing in the dump depicts "a game", but a
    // zombie head is what the game counts, and it is the same icon the lawn already uses for progress.
    private static final String ICON_GAMES = "image_ui_hud_ingame_progress_meter_zombiehead";
    private static final String DIVIDER = "image_ui_generic_4pxdivider";

    // A stat is a quiet label and a loud number. Without the split every figure competes with its own
    // caption and the block reads as a paragraph, which is what it looked like before.
    private static final Color LABEL_TINT = new Color(0.76f, 0.70f, 0.56f, 1f);
    private static final Color VALUE_TINT = new Color(0.62f, 1f, 0.55f, 1f);
    private static final Color NAME_TINT = new Color(1f, 0.84f, 0.32f, 1f);
    private static final Color FAINT = new Color(0.68f, 0.66f, 0.62f, 1f);

    private static final float FIELD_WIDTH = 320f;
    private static final float FIELD_HEIGHT = 42f;

    // Vertical rhythm: rows within a section, then the larger gap that separates the two sections.
    //
    // Both are as generous as the design space allows. The panel is ~690 tall in a 720 viewport, so
    // there is no room left to spend: anything larger and the frame's rounded corners run off the top
    // and bottom of the screen.
    private static final float ROW_GAP = 10f;
    private static final float SECTION_GAP = 20f;

    private static final float ICON_SIZE = 30f;
    private static final float CAPTION_WIDTH = 150f;
    private static final float VALUE_WIDTH = 62f;

    // Text should not start against the field's inner edge. The skin's field art carries almost no
    // horizontal padding of its own, so this is added on top of whatever it reports.
    private static final float FIELD_TEXT_PAD = 14f;

    private Label nickname;
    private Label account;
    private Label coins;
    private Label gems;
    private Label games;
    private Label levels;
    private Label meowPoints;

    private TextField newUsername;
    private TextField newNickname;
    private TextField newEmail;
    private TextField oldPassword;
    private TextField newPassword;

    public ProfileScreen(GdxContext context) {
        super(context);
    }

    @Override
    protected void build(Table root) {
        Table panel = MenuPanel.build(skin, "Your Profile");

        panel.add(identity()).padBottom(8f).row();
        panel.add(statsGrid()).padBottom(10f).row();
        panel.add(divider()).width(470f).height(3f).padBottom(10f).row();
        panel.add(editSection()).row();
        panel.add(passwordSection()).padBottom(14f).row();
        panel.add(backButton()).width(210f).height(44f).row();

        root.setFillParent(true);
        root.add(panel);
    }

    // Who you are: the nickname carries the weight, the account details sit under it as fine print.
    private Table identity() {
        Table block = new Table();
        nickname = MenuStyles.label(skin, "", MenuStyles.TITLE);
        nickname.setColor(NAME_TINT);
        account = MenuStyles.label(skin, "", MenuStyles.TEXT);
        account.setColor(FAINT);
        account.setAlignment(Align.center);

        block.add(nickname).padBottom(2f).row();
        block.add(account);
        return block;
    }

    // Five figures in a 2 x 3 grid rather than five lines of prose. The grid is what makes them
    // scannable: the eye finds a number by position instead of by reading.
    //
    // ONE table of six columns -- icon, caption, value, twice over -- rather than a table of little
    // per-stat tables. Nested tables each size to their own contents, so "Coins 19400" and
    // "Best Meow Points 747" put their numbers at different x and the block reads as ragged even when
    // the outer columns are uniform. Six real columns line every icon, caption and number up exactly.
    private Table statsGrid() {
        coins = value();
        gems = value();
        games = value();
        levels = value();
        meowPoints = value();

        Table grid = new Table();
        grid.defaults().padTop(4f).padBottom(4f);
        // Captions share a width so the numbers start at the same place in both halves.
        grid.columnDefaults(1).width(CAPTION_WIDTH).left().padLeft(8f);
        grid.columnDefaults(4).width(CAPTION_WIDTH).left().padLeft(8f);
        grid.columnDefaults(2).width(VALUE_WIDTH).left().padRight(18f);
        grid.columnDefaults(5).width(VALUE_WIDTH).left();

        addStat(grid, ICON_COINS, "Coins", coins);
        addStat(grid, ICON_GEMS, "Gems", gems);
        grid.row();
        addStat(grid, ICON_STAR, "Levels cleared", levels);
        addStat(grid, ICON_GAMES, "Games played", games);
        grid.row();

        // The odd one out, centred under the two columns rather than left in a lopsided half.
        Table last = new Table();
        last.add(icon(ICON_STAR)).size(ICON_SIZE, ICON_SIZE).padRight(8f);
        last.add(caption("Best Meow Points")).padRight(10f);
        last.add(meowPoints);
        grid.add(last).colspan(6).center().padTop(6f);
        return grid;
    }

    private void addStat(Table grid, String iconId, String text, Label number) {
        grid.add(icon(iconId)).size(ICON_SIZE, ICON_SIZE);
        grid.add(caption(text));
        grid.add(number);
    }

    // Fit, so a 54x33 coin pile, an 81x62 gem cluster and a zombie head all sit in the same square box
    // without any of them being stretched to it.
    private Image icon(String iconId) {
        Drawable art = MenuStyles.drawable(skin, iconId);
        Image image = art == null ? new Image() : new Image(art);
        image.setScaling(Scaling.fit);
        return image;
    }

    private Label caption(String text) {
        Label label = MenuStyles.label(skin, text, MenuStyles.TEXT);
        label.setColor(LABEL_TINT);
        label.setAlignment(Align.left);
        return label;
    }

    private Label value() {
        Label label = MenuStyles.label(skin, "0", MenuStyles.TEXT);
        label.setColor(VALUE_TINT);
        return label;
    }

    private Image divider() {
        Drawable art = MenuStyles.drawable(skin, DIVIDER);
        Image line = art == null ? new Image() : new Image(art);
        line.setColor(0.55f, 0.48f, 0.36f, 0.9f);
        return line;
    }

    // Widens the field's inner text inset without touching the shared skin style, by wrapping its
    // background in a drawable that simply reports more left and right padding. TextField takes its
    // text inset from background.getLeftWidth(), so that is the whole mechanism.
    private TextField pad(TextField field) {
        TextField.TextFieldStyle style = new TextField.TextFieldStyle(field.getStyle());
        if (style.background != null) {
            style.background = MenuStyles.insetBy(style.background, FIELD_TEXT_PAD);
        }
        if (style.focusedBackground != null) {
            style.focusedBackground = MenuStyles.insetBy(style.focusedBackground, FIELD_TEXT_PAD);
        }
        field.setStyle(style);
        return field;
    }

    private Table editSection() {
        newUsername = pad(MenuStyles.field(skin, "new username"));
        newNickname = pad(MenuStyles.field(skin, "new nickname"));
        newEmail = pad(MenuStyles.field(skin, "new email"));

        Table section = new Table();
        section.add(editRow(newUsername, "Rename", this::changeUsername)).padBottom(ROW_GAP).row();
        section.add(editRow(newNickname, "Set", this::changeNickname)).padBottom(ROW_GAP).row();
        section.add(editRow(newEmail, "Update", this::changeEmail)).row();
        return section;
    }

    private Table passwordSection() {
        oldPassword = pad(MenuStyles.secret(skin, "current password"));
        newPassword = pad(MenuStyles.secret(skin, "new password"));

        Table section = new Table();
        Label heading = MenuStyles.label(skin, "Change Password", MenuStyles.HEADING);
        // Extra air above the sub-header, so the two halves of the form read as two halves.
        section.add(heading).padTop(SECTION_GAP - ROW_GAP).padBottom(10f).row();
        section.add(oldPassword).width(FIELD_WIDTH + 128f).height(FIELD_HEIGHT).padBottom(ROW_GAP)
                .row();
        section.add(editRow(newPassword, "Change", this::changePassword)).row();
        return section;
    }

    // A field and the button that submits it, so each edit is obviously independent -- this screen has
    // no "save all", because each change is its own Command with its own validation and its own answer.
    private Table editRow(TextField field, String action, Runnable onSubmit) {
        Table row = new Table();
        row.add(field).width(FIELD_WIDTH).height(FIELD_HEIGHT).padRight(12f);
        row.add(actionButton(action, onSubmit)).width(116f).height(FIELD_HEIGHT);
        return row;
    }

    // Hover swell, press sink and spring-back all arrive with MenuStyles.button -- see ButtonJuice.
    // All this adds is what the button does.
    private TextButton actionButton(String text, Runnable onSubmit) {
        TextButton button = MenuStyles.button(skin, text, MenuStyles.BUTTON_BROWN);
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                onSubmit.run();
            }
        });
        return button;
    }

    // A firmer pop than the shared entrance: this panel is taller than the others, and a gentle 0.94
    // barely registered on it.
    @Override
    protected float entranceScale() {
        return 0.8f;
    }

    // The success/failure toast comes from the model side: each of these Commands ends by calling
    // ProfileMenuRenderer, and the graphical build's implementation raises the sentence as a toast --
    // green when the change went through, red when a validator refused it. Nothing about that is this
    // screen's business, which is why it is not duplicated here.
    //
    // What IS this screen's business is the field. It used to be cleared whenever the command was
    // POSTED, which is not the same as accepted: a rejected username wiped what the player typed and
    // left them re-entering it to read their own mistake. So each edit checks the model afterwards and
    // only clears on a real change.
    private void changeUsername() {
        String value = newUsername.getText().trim();
        if (!MenuForms.filled(context, value, "new username")) {
            return;
        }
        String before = username();
        commands.submit("menu profile change-username -u " + value);
        clearIfChanged(newUsername, before, username());
    }

    private void changeNickname() {
        String value = newNickname.getText().trim();
        if (!MenuForms.filled(context, value, "new nickname")) {
            return;
        }
        String before = nickname();
        commands.submit("menu profile change-nickname -u " + value);
        clearIfChanged(newNickname, before, nickname());
    }

    private void changeEmail() {
        String value = newEmail.getText().trim();
        if (!MenuForms.filled(context, value, "new email")) {
            return;
        }
        String before = email();
        commands.submit("menu profile change-email -e " + value);
        clearIfChanged(newEmail, before, email());
    }

    private void clearIfChanged(TextField field, String before, String after) {
        if (before != null && !before.equals(after)) {
            field.setText("");
        }
    }

    private String username() {
        User user = context.appSession().getCurrentUser();
        return user == null ? null : user.getUsername();
    }

    private String nickname() {
        User user = context.appSession().getCurrentUser();
        return user == null ? null : user.getNickname();
    }

    private String email() {
        User user = context.appSession().getCurrentUser();
        return user == null ? null : user.getEmail();
    }

    private String passwordHash() {
        User user = context.appSession().getCurrentUser();
        return user == null ? null : user.getHashPassword();
    }

    // The old password is required, and is checked by the Command rather than here -- this screen must
    // never be the thing that decides whether a password change is allowed.
    private void changePassword() {
        String fresh = newPassword.getText();
        String current = oldPassword.getText();
        if (!MenuForms.filled(context, fresh, "new password")
                || !MenuForms.require(context, current,
                        "Enter your current password to change it.")) {
            return;
        }
        // The stored hash is the only observable proof a password change took: a wrong current password
        // is refused inside the Command, and clearing both fields on refusal would make the player
        // retype a password they had entered correctly.
        String before = passwordHash();
        commands.submit("menu profile change-password -p " + fresh + " -o " + current);
        if (before != null && !before.equals(passwordHash())) {
            oldPassword.setText("");
            newPassword.setText("");
        }
    }

    private TextButton backButton() {
        return actionButton("Back", this::goBack);
    }

    @Override
    protected void refresh() {
        User user = context.appSession().getCurrentUser();
        if (user == null) {
            nickname.setText("Not signed in");
            account.setText("");
            return;
        }
        Profile profile = user.getProfile();
        nickname.setText(user.getNickname());
        account.setText(user.getUsername() + "   -   " + user.getEmail());

        coins.setText(String.valueOf(profile.getCoins()));
        gems.setText(String.valueOf(profile.getGems()));
        games.setText(String.valueOf(profile.getGameNumbers()));
        levels.setText(String.valueOf(clearedLevels(profile)));
        meowPoints.setText(String.valueOf(profile.getBestNumberOfMeowPoints()));
    }

    // Four levels to a chapter, and both counters point at what is NEXT rather than what is done.
    private int clearedLevels(Profile profile) {
        return (profile.getLastChapter() - 1) * 4 + profile.getLastLevel() - 1;
    }
}
