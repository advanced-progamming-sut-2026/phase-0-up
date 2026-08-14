package views.gdx.screens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Align;
import models.user.Profile;
import models.user.User;
import utils.Constants;
import utils.storage.DatabaseManager;
import views.gdx.core.GdxContext;
import views.gdx.ui.Cycler;
import views.gdx.ui.DifficultyMeter;
import views.gdx.ui.MenuStyles;

// Settings.
//
// Difficulty goes through ChangeDifficultyCommand, because it has a rule (which levels are valid) and
// a message, and both belong to the model. The other three are pure preferences with no rule to
// enforce, so they are written to the Profile directly and saved -- routing them through a Command
// would mean inventing three Commands, three regexes and three renderer methods for the terminal
// build, which has no window to draw a grid on and no accumulator to speed up.
//
// Laid out as a two-column form: captions right-aligned on the left, controls left-aligned on the
// right. A centred stack made every row its own island and left the arrows stranded from their value.
public final class SettingsScreen extends MenuScreen {

    private static final String WRENCH = "image_ui_mainmenu_mm_settings";
    private static final float WRENCH_SIZE = 46f;

    private static final String[] SPEEDS = {"Normal", "Fast (2x)", "Frantic (3x)"};

    // Five, because the model has always had five: Constants.MAX_DIFFICULTY_LEVEL is 5 and everything
    // that reads the setting scales against DEFAULT_DIFFICULTY_LEVEL (3). This screen was the only
    // thing capping it at three, so two of the model's settings were unreachable.
    private static final String[] DIFFICULTIES = {
        "1 - Gentle", "2 - Easy", "3 - Normal", "4 - Hard", "5 - Brutal"
    };

    private static final Color CAPTION_TINT = new Color(0.95f, 0.86f, 0.55f, 1f);
    private static final Color HINT_TINT = new Color(0.82f, 0.80f, 0.76f, 0.7f);

    private static final float CAPTION_WIDTH = 190f;

    // Every control in the right column is given this width and centred in it, so the arrow selectors
    // and the switches share one vertical axis. Left to size themselves, a cycler and a toggle are
    // different widths and each row centred on its own middle -- which is what made the column look
    // like four unrelated controls that happened to be stacked.
    private static final float CONTROL_WIDTH = 250f;

    // The gauge lives in its own third column rather than inside the control. Putting it in the control
    // would make the difficulty row wider than the others and break the very alignment above, and
    // leaving it to float at the panel's edge is what made it feel detached from the selector.
    private static final float METER_WIDTH = 96f;
    private static final float METER_HEIGHT = 104f;

    // Narrower than the control column so the switch is centred in it rather than filling it -- a
    // full-width switch would read as a banner rather than as a control.
    private static final float SWITCH_WIDTH = 150f;

    private static final float ROW_GAP = 12f;

    private Cycler difficulty;
    private Cycler gameSpeed;
    private DifficultyMeter meter;

    public SettingsScreen(GdxContext context) {
        super(context);
    }

    @Override
    protected void build(Table root) {
        Profile profile = profile();

        Table panel = MenuPanel.build(skin, null);
        panel.add(titleWithWrench()).padBottom(18f).row();
        panel.add(form(profile)).row();
        panel.add(hint()).width(430f).padTop(4f).padBottom(16f).row();
        panel.add(backButton()).width(210f).height(46f).row();

        root.setFillParent(true);
        root.add(panel);
    }

    // The two-column grid. Every caption shares one column width, so the controls all start at the same
    // x -- which is what makes a form read as a form rather than as four unrelated rows.
    // Three columns: caption, control, gauge. The middle one is a fixed width every control is centred
    // in, which is what puts the arrow selectors and the switches on one vertical axis.
    private Table form(Profile profile) {
        Table form = new Table();
        form.defaults().padBottom(ROW_GAP);
        form.columnDefaults(0).width(CAPTION_WIDTH).right().padRight(16f);
        form.columnDefaults(1).width(CONTROL_WIDTH).center();
        form.columnDefaults(2).width(METER_WIDTH).left().padLeft(2f);

        int level = currentDifficulty(profile);
        difficulty = new Cycler(skin, DIFFICULTIES, level - 1).onChange(this::setDifficulty);
        meter = new DifficultyMeter(context.sprites().get(DifficultyMeter.SPRITE), level);

        form.add(caption("Difficulty:"));
        form.add(difficulty);
        // The gauge is why a five-position difficulty is worth having on a menu at all: "4 - Hard" is a
        // string, an angrier pepper is a feeling. Its own column, immediately right of the selector's
        // arrow, so the two read as one control without the difficulty row growing wider than the rest.
        form.add(meter).size(METER_WIDTH, METER_HEIGHT).row();

        gameSpeed = new Cycler(skin, SPEEDS, speedIndex(profile))
                .onChange(index -> apply(p -> p.setGameSpeed(index + Constants.MIN_GAME_SPEED)));
        form.add(caption("Game speed:"));
        form.add(gameSpeed);
        form.add().row();

        form.add(caption("Show grid:"));
        form.add(switchBox(profile != null && profile.isShowGrid(),
                on -> apply(p -> p.setShowGrid(on))));
        form.add().row();

        form.add(caption("Debug mode:"));
        form.add(switchBox(profile != null && profile.isDebugMode(),
                on -> apply(p -> p.setDebugMode(on))));
        form.add().row();
        return form;
    }

    // Index is 0-based; the command and the model are 1-based.
    private void setDifficulty(int index) {
        int level = index + 1;
        commands.submit("menu settings change-difficulty -l " + level);
        // Driven off the model rather than the click, so the gauge can only ever show a level the
        // command actually accepted.
        meter.setDifficultyLevel(currentDifficulty(profile()));
    }

    private int currentDifficulty(Profile profile) {
        if (profile == null) {
            return Constants.DEFAULT_DIFFICULTY_LEVEL;
        }
        return Math.max(1, Math.min(DIFFICULTIES.length, profile.getDifficultyLevel()));
    }

    private Label caption(String text) {
        Label label = MenuStyles.label(skin, text, MenuStyles.TEXT);
        label.setColor(CAPTION_TINT);
        label.setAlignment(Align.right);
        return label;
    }

    // A real switch rather than another brown button. The skin ships the main menu's own checkbox art,
    // which is what a stateful control is supposed to look like -- the previous toggles were the same
    // shape and colour as Back, so nothing about them said "this is a state, not an action".
    private Table switchBox(boolean initial, java.util.function.Consumer<Boolean> onChange) {
        // The caption already says which setting this is, so the switch only has to say On or Off.
        TextButton box = MenuStyles.toggle(skin, "", initial, onChange);

        // Centred in a holder the width of the control column, so a switch and a cycler share a middle.
        Table holder = new Table();
        holder.add(box).width(SWITCH_WIDTH).height(46f).center();
        return holder;
    }

    private Label hint() {
        Label label = MenuStyles.label(skin, "Debug mode shows the cheat panel on the lawn.",
                MenuStyles.TEXT);
        label.setColor(HINT_TINT);
        // Secondary text: small enough and faint enough that it annotates the form rather than
        // competing with it.
        label.setFontScale(0.7f);
        label.setWrap(true);
        label.setAlignment(Align.center);
        return label;
    }

    // "[wrench] Settings". The icon turns slowly rather than sitting still, which is the same idea as
    // the drifting motes: nothing on a menu should be completely inert.
    private Table titleWithWrench() {
        Table row = new Table();
        Drawable art = MenuStyles.drawable(skin, WRENCH);
        if (art != null) {
            Image wrench = new Image(art);
            wrench.setOrigin(Align.center);
            wrench.addAction(Actions.forever(Actions.sequence(
                    Actions.rotateBy(14f, 2.4f, Interpolation.sine),
                    Actions.rotateBy(-14f, 2.4f, Interpolation.sine))));
            row.add(wrench).size(WRENCH_SIZE).padRight(12f);
        }
        row.add(MenuStyles.title(skin, "Settings"));
        return row;
    }

    // The same firmer pop the profile panel uses, for the same reason: a tall panel barely registers
    // the shared entrance's gentle 0.94. Only the starting scale differs -- the fade and the rise are
    // the base screen's, so every menu arrives the same way.
    @Override
    protected float entranceScale() {
        return 0.8f;
    }

    private int speedIndex(Profile profile) {
        return profile == null ? 0 : profile.getGameSpeed() - Constants.MIN_GAME_SPEED;
    }

    // Written straight through and saved immediately. A setting the player changed and then lost to a
    // crash is worse than a slightly chattier save.
    private void apply(java.util.function.Consumer<Profile> change) {
        Profile profile = profile();
        if (profile == null) {
            context.toasts().error("Sign in first -- settings are saved to your account.");
            return;
        }
        change.accept(profile);
        DatabaseManager.getInstance().saveAll();
    }

    private Profile profile() {
        User user = context.appSession().getCurrentUser();
        return user == null ? null : user.getProfile();
    }

    private TextButton backButton() {
        TextButton button = MenuStyles.button(skin, "Back", MenuStyles.BUTTON_BROWN);
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                goBack();
            }
        });
        return button;
    }
}
