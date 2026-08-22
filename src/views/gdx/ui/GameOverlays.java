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
        outcomeArt = gameOverArt();
        outcome = panel(outcomeArt, outcomeTitle, outcomeBody,
                button("Continue", MenuStyles.BUTTON_GREEN, onContinue));

        for (Table overlay : new Table[] {objective, pause, outcome}) {
            overlay.setVisible(false);
            stage.addActor(overlay);
        }
    }

    // The game's own fail screen: a bitten brain on a plate, 586x383 at the 768 resolution. Shown only
    // when the player LOSES -- "The Zombies Ate Your Brains" is a caption on a picture in the real game,
    // and a title alone over an empty panel is the weakest of the three interruptions to look at.
    //
    // Null when the atlas cannot supply it, in which case the panel is exactly what it was before.
    private static final String GAME_OVER_ART = "IMAGE_UI_GAMEOVER_FAIL_SCREEN_BRAIN_ONLY";
    private static final float GAME_OVER_WIDTH = 360f;

    private final com.badlogic.gdx.scenes.scene2d.ui.Image outcomeArt;

    // The cell holding it, so a win can collapse it to nothing.
    private com.badlogic.gdx.scenes.scene2d.ui.Cell<?> artCell;

    private com.badlogic.gdx.scenes.scene2d.ui.Image gameOverArt() {
        try {
            com.badlogic.gdx.graphics.g2d.TextureRegion region = assets.region(GAME_OVER_ART);
            com.badlogic.gdx.scenes.scene2d.ui.Image art =
                    new com.badlogic.gdx.scenes.scene2d.ui.Image(
                            new com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable(region));
            art.setScaling(com.badlogic.gdx.utils.Scaling.fit);
            return art;
        } catch (RuntimeException missing) {
            return null;   // decoration only; the panel still says what happened
        }
    }

    private Table panel(Label title, Label text, TextButton... buttons) {
        return panel(null, title, text, buttons);
    }

    // Dimmed full-screen layer with a framed panel in the middle. `art` may be null.
    private Table panel(com.badlogic.gdx.scenes.scene2d.ui.Image art, Label title, Label text,
                        TextButton... buttons) {
        Table layer = new Table();
        layer.setFillParent(true);
        layer.setBackground(assets.solid(DIM));

        Table box = new Table();
        com.badlogic.gdx.scenes.scene2d.utils.Drawable frame =
                MenuStyles.drawable(skin, MenuStyles.PANEL_BORDER);
        if (frame != null) {
            // The frame is translucent through its middle -- fine over a menu's near-black backdrop,
            // not fine over a lit lawn, where zombies and headstones read straight through the text.
            // Same fix the menus use: an opaque fill tucked under the border. It matters more here,
            // because the game-over art itself is a brain on a white plate and the board showed
            // through the plate.
            box.setBackground(layered(MenuStyles.panelFill(skin), frame));
        }
        box.pad(30f, 52f, 30f, 52f);
        if (art != null) {
            // Height follows the source's aspect, so the plate is not squashed. The cell is kept
            // because showOutcome has to collapse it on a win, and it lives in this inner box rather
            // than in the full-screen layer that is returned.
            artCell = box.add(art).width(GAME_OVER_WIDTH)
                    .height(GAME_OVER_WIDTH * 383f / 586f).padBottom(8f);
            box.row();
        }
        box.add(title).padBottom(10f).row();
        box.add(text).width(440f).padBottom(16f).row();
        for (TextButton button : buttons) {
            box.add(button).width(280f).height(56f).padBottom(8f).row();
        }

        layer.add(box);
        return layer;
    }

    // How far the fill is pulled inside the frame's bounds. The frame is a rounded shape drawn in a
    // rectangular region, so a fill drawn to the same rectangle shows as square shoulders sticking out
    // past the gold edge; insetting tucks it under the border, which is thick enough to cover the seam.
    private static final float FILL_INSET = 18f;

    // Draws one drawable over another as a single background. Sizing and padding come from the top
    // one, since that is the frame the layout was tuned against. Same shape as MenuPanel's, which is
    // private to that class and belongs to the menu screens rather than to the lawn.
    private static com.badlogic.gdx.scenes.scene2d.utils.Drawable layered(
            com.badlogic.gdx.scenes.scene2d.utils.Drawable under,
            com.badlogic.gdx.scenes.scene2d.utils.Drawable over) {
        if (under == null) {
            return over;
        }
        return new com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable(over) {
            @Override
            public void draw(com.badlogic.gdx.graphics.g2d.Batch batch,
                             float x, float y, float width, float height) {
                under.draw(batch, x + FILL_INSET, y + FILL_INSET,
                        width - FILL_INSET * 2f, height - FILL_INSET * 2f);
                over.draw(batch, x, y, width, height);
            }
        };
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
        if (outcomeArt != null) {
            // Losing gets the picture; winning does not. A half-eaten brain over "Level Complete!"
            // would be congratulating the wrong side.
            outcomeArt.setVisible(!won);
            // Hidden actors still hold their cell, so the cell is collapsed as well -- otherwise a win
            // panel is a title floating under 235 units of empty frame.
            //
            // Through the saved cell, NOT outcome.getCell(outcomeArt): `outcome` is the full-screen
            // dimmed layer and the art lives in the framed box INSIDE it, so getCell returned null and
            // every loss threw an NPE out of the render loop -- the one moment the panel exists for.
            if (artCell != null) {
                artCell.height(won ? 0f : GAME_OVER_WIDTH * 383f / 586f);
                artCell.padBottom(won ? 0f : 8f);
                outcome.invalidateHierarchy();
            }
        }
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
