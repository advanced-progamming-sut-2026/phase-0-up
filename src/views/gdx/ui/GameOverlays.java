package views.gdx.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import views.gdx.core.Assets;

// The three panels that interrupt a level: its objective at the start, the pause menu, and the result
// at the end.
//
// All three are one shape -- dimmed screen, framed panel, a title, a line of text, some buttons -- so
// they are built by one method here rather than three times across GameScreen. They live on the HUD's
// Stage, which is already a 1280x720 FitViewport sitting above the lawn's camera; putting them on the
// board's camera instead would scale them with the zoom.
public final class GameOverlays {

    // Darker than the menus' scrim: this one covers a board that is still drawn underneath, and the
    // buttons have to win against a lawn full of moving colour.
    private static final Color DIM = new Color(0f, 0f, 0f, 0.62f);

    private final Assets assets;
    private final Skin skin;

    private final Table objective;
    private final Table pause;
    private final Table outcome;

    private final Label objectiveTitle;
    private final Label objectiveBody;
    private final Label outcomeTitle;
    private final Label outcomeBody;

    public GameOverlays(Assets assets, Stage stage, Runnable onStart, Runnable onResume,
                        Runnable onRestart, Runnable onSaveAndExit, Runnable onContinue) {
        this.assets = assets;
        this.skin = assets.skin();

        objectiveTitle = MenuStyles.label(skin, "", MenuStyles.TITLE);
        objectiveBody = body();
        objective = panel(objectiveTitle, objectiveBody,
                button("Let's Rock!", MenuStyles.BUTTON_GREEN, onStart));

        pause = panel(MenuStyles.label(skin, "Paused", MenuStyles.TITLE),
                body("P or Space resumes.  Esc drops the held tool."),
                button("Resume", MenuStyles.BUTTON_GREEN, onResume),
                button("Restart Level", MenuStyles.BUTTON_BROWN, onRestart),
                button("Save and Exit", MenuStyles.BUTTON_PURPLE, onSaveAndExit));

        outcomeTitle = MenuStyles.label(skin, "", MenuStyles.TITLE);
        outcomeBody = body();
        outcome = panel(outcomeTitle, outcomeBody,
                button("Continue", MenuStyles.BUTTON_GREEN, onContinue));

        for (Table overlay : new Table[] {objective, pause, outcome}) {
            overlay.setVisible(false);
            stage.addActor(overlay);
        }
    }

    // Dimmed full-screen layer with a framed panel in the middle.
    private Table panel(Label title, Label text, TextButton... buttons) {
        Table layer = new Table();
        layer.setFillParent(true);
        layer.setBackground(assets.solid(DIM));

        Table box = new Table();
        com.badlogic.gdx.scenes.scene2d.utils.Drawable frame =
                MenuStyles.drawable(skin, MenuStyles.PANEL_BORDER);
        if (frame != null) {
            box.setBackground(frame);
        }
        box.pad(30f, 52f, 30f, 52f);
        box.add(title).padBottom(10f).row();
        box.add(text).width(440f).padBottom(16f).row();
        for (TextButton button : buttons) {
            box.add(button).width(280f).height(56f).padBottom(8f).row();
        }

        layer.add(box);
        return layer;
    }

    private Label body() {
        return body("");
    }

    private Label body(String text) {
        Label label = MenuStyles.label(skin, text, MenuStyles.TEXT);
        label.setWrap(true);
        label.setAlignment(Align.center);
        return label;
    }

    private TextButton button(String text, String style, Runnable action) {
        TextButton button = MenuStyles.button(skin, text, style);
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (action != null) {
                    action.run();
                }
            }
        });
        return button;
    }

    // What this level is asking of the player, before the first zombie arrives.
    public void showObjective(String levelName, String goal) {
        objectiveTitle.setText(levelName);
        objectiveBody.setText(goal);
        objective.setVisible(true);
    }

    public boolean isObjectiveVisible() {
        return objective.isVisible();
    }

    public void hideObjective() {
        objective.setVisible(false);
    }

    public void setPauseVisible(boolean visible) {
        pause.setVisible(visible);
    }

    // The result. The two sentences are the model's own, taken from the events GameEngine already
    // emits, so the overlay and the toast that preceded it cannot disagree about what happened.
    public void showOutcome(boolean won, String message) {
        outcomeTitle.setText(won ? "Level Complete!" : "The Zombies Ate Your Brains");
        outcomeBody.setText(message);
        outcome.setVisible(true);
    }

    public boolean isOutcomeVisible() {
        return outcome.isVisible();
    }

    // True while any of the three is up, so the lawn can stop taking clicks behind them.
    public boolean isAnyVisible() {
        return objective.isVisible() || pause.isVisible() || outcome.isVisible();
    }
}
