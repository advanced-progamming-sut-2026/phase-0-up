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

    private static final float BUTTON_HEIGHT = 56f;

    // Clear of the seed bank, which is drawn behind the dim layer at the top-left.
    private static final float OBJECTIVE_TOP_PAD = 26f;

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
        questList = new Table();
        objective = panel(null, questList, objectiveTitle, objectiveBody,
                button("Let's Rock!", MenuStyles.BUTTON_GREEN, onStart));
        objectiveExtraCell = extraCell;
        // The one panel that is not centred.
        //
        // It shares the screen with the NPC greeting, which lives at the bottom of the same Stage, and
        // once the quest list was added to this card the two overlapped -- the speaker's box sat across
        // "Let's Rock!". Anchoring the card to the top gives each of them its own half, and a title card
        // above a speaker is the arrangement this moment wants anyway. The pause and result panels have
        // nothing else on screen and stay in the middle.
        objective.top().padTop(OBJECTIVE_TOP_PAD);

        pause = panel(MenuStyles.label(skin, "Paused", MenuStyles.TITLE),
                body("P or Space resumes.  Esc drops the held tool."),
                button("Resume", MenuStyles.BUTTON_GREEN, onResume),
                button("Restart Level", MenuStyles.BUTTON_BROWN, onRestart),
                button("Save and Exit", MenuStyles.BUTTON_PURPLE, onSaveAndExit));

        outcomeTitle = MenuStyles.label(skin, "", MenuStyles.TITLE);
        outcomeBody = body();
        outcomeArt = gameOverArt();
        scorecard = new Table();
        // Built before the panel so the loop that lays the buttons out can recognise it and keep its
        // cell -- a win collapses the row away, exactly as it collapses the brain and the scorecard.
        retryButton = button("Try Again", MenuStyles.BUTTON_BROWN, onRestart);
        outcome = panel(outcomeArt, scorecard, outcomeTitle, outcomeBody,
                retryButton,
                button("Continue", MenuStyles.BUTTON_GREEN, onContinue));
        scoreCell = extraCell;

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

    // Where panel() leaves the cell of whatever `extra` table it was given, for the caller to keep.
    //
    // A scratch field rather than a return value because panel() already returns the layer, and two of
    // the three panels now pass an `extra`: the outcome's scorecard and the objective's quest list.
    // Assigning straight to `scoreCell` inside panel() was fine while only one caller did, and would
    // now have the objective panel quietly steal the outcome panel's cell.
    private com.badlogic.gdx.scenes.scene2d.ui.Cell<?> extraCell;

    // "Try Again" on the result panel, and its cell. Shown only on a loss: a win's next move is
    // Continue, and offering to replay a level that was just beaten reads as though it was not.
    private final TextButton retryButton;
    private com.badlogic.gdx.scenes.scene2d.ui.Cell<?> retryCell;

    // What the level is asking of the player beyond surviving: the quests still outstanding. Empty and
    // collapsed on a profile that has finished them all.
    private final Table questList;
    private com.badlogic.gdx.scenes.scene2d.ui.Cell<?> objectiveExtraCell;

    // The scoring game's end-of-level breakdown, and the cell that collapses it away on every other
    // level. Built empty and filled by showOutcome, because what it says is only known when the run
    // ends.
    private final Table scorecard;
    private com.badlogic.gdx.scenes.scene2d.ui.Cell<?> scoreCell;

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
        return panel(null, null, title, text, buttons);
    }

    // Dimmed full-screen layer with a framed panel in the middle. `art` and `extra` may both be null;
    // only the outcome panel passes either. Passed in rather than read off a field, because an Actor
    // has exactly one parent -- a shared builder reading `scorecard` directly would try to adopt the
    // same table into all three panels, and the last one to ask would win.
    private Table panel(com.badlogic.gdx.scenes.scene2d.ui.Image art, Table extra, Label title,
                        Label text, TextButton... buttons) {
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
        // The Meow Point breakdown, empty and zero-height on every level that is not the scoring game.
        // Its cell is kept for the same reason the art's is: a hidden actor still holds its cell, and a
        // collapsed one is what stops an ordinary win panel growing a band of empty frame. See
        // scene2d-nested-cell-trap -- this cell lives in the inner box, not in the returned layer.
        extraCell = null;
        if (extra != null) {
            extraCell = box.add(extra).width(440f).height(0f);
            box.row();
        }
        for (TextButton button : buttons) {
            com.badlogic.gdx.scenes.scene2d.ui.Cell<?> cell =
                    box.add(button).width(280f).height(BUTTON_HEIGHT).padBottom(8f);
            if (button == retryButton) {
                retryCell = cell;
            }
            box.row();
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

    // How many outstanding quests the card lists before it stops and counts the rest.
    //
    // Three, because this panel is read in the two seconds before a level starts and a wall of them is
    // read as decoration. The Travel Log is where the full list lives; this is a reminder of what is
    // worth going for THIS match.
    private static final int QUESTS_SHOWN = 3;

    private static final Color QUEST_HEADING = new Color(1f, 0.86f, 0.32f, 1f);
    private static final Color QUEST_DETAIL = new Color(0.80f, 0.78f, 0.74f, 1f);

    // What this level is asking of the player, before the first zombie arrives.
    public void showObjective(String levelName, String goal) {
        showObjective(levelName, goal, java.util.List.of(), null);
    }

    // The same card, plus the quests still outstanding.
    //
    // Reads Quest and Profile directly, on the same footing as showOutcome reading MeowPointManager: a
    // view may look at the model, and building the rows here rather than handing in pre-formatted
    // strings keeps the panel's layout in the class that owns the panel.
    public void showObjective(String levelName, String goal, java.util.List<models.quests.Quest> open,
                              models.user.Profile profile) {
        objectiveTitle.setText(levelName);
        objectiveBody.setText(goal);
        buildQuestList(open, profile);
        if (objectiveExtraCell != null) {
            boolean any = questList.getChildren().size > 0;
            objectiveExtraCell.height(any ? questList.getPrefHeight() : 0f);
            objectiveExtraCell.padBottom(any ? 16f : 0f);
        }
        objective.invalidateHierarchy();
        objective.setVisible(true);
        // In front of anything added to this Stage AFTER these three were built -- which is every score
        // popup, since they are created as they happen. Scene2D draws in child order, so without this a
        // Meow Point award floats up THROUGH the panel that is reporting it.
        objective.toFront();
    }

    // The outstanding quests, most important first -- the list arrives already ranked, so this only
    // takes the top of it and counts what it left behind.
    //
    // A quest's own progress line comes from QuestProgress.describe(), the same source the Travel Log's
    // bars and tick boxes read, so the two cannot tell the player different numbers.
    private void buildQuestList(java.util.List<models.quests.Quest> open, models.user.Profile profile) {
        questList.clearChildren();
        if (open == null || open.isEmpty()) {
            return;
        }
        Label heading = MenuStyles.label(skin, "Still to do", MenuStyles.HEADING);
        heading.setFontScale(0.7f);
        heading.setColor(QUEST_HEADING);
        heading.setAlignment(Align.left);
        questList.add(heading).left().growX().padBottom(4f).row();

        int shown = Math.min(QUESTS_SHOWN, open.size());
        for (int i = 0; i < shown; i++) {
            questList.add(questRow(open.get(i), profile)).left().growX().row();
        }
        if (open.size() > shown) {
            Label more = MenuStyles.label(skin,
                    "+" + (open.size() - shown) + " more in the Travel Log.", MenuStyles.TEXT);
            more.setFontScale(0.72f);
            more.setColor(QUEST_DETAIL);
            more.setAlignment(Align.left);
            questList.add(more).left().growX().padTop(2f).row();
        }
    }

    // One line: the quest's name, and how far along it is where that is a number worth printing. A
    // single-level quest has no running total -- see TravelLogScreen.goalRow -- so it simply gets its
    // name, which on this card is the whole of what the player needs.
    private Table questRow(models.quests.Quest quest, models.user.Profile profile) {
        Table row = new Table();
        Label name = MenuStyles.label(skin, "- " + quest.getName(), MenuStyles.TEXT);
        name.setFontScale(0.8f);
        name.setAlignment(Align.left);
        row.add(name).left().expandX();

        models.quests.QuestProgress progress =
                profile == null ? null : quest.getProgress(profile);
        if (progress != null && progress.isMeasurable() && progress.crossLevel()) {
            Label count = MenuStyles.label(skin,
                    Math.min(progress.current(), progress.target()) + " / " + progress.target(),
                    MenuStyles.TEXT);
            count.setFontScale(0.8f);
            count.setColor(QUEST_DETAIL);
            row.add(count).right().padLeft(12f);
        }
        return row;
    }

    public boolean isObjectiveVisible() {
        return objective.isVisible();
    }

    public void hideObjective() {
        objective.setVisible(false);
    }

    public void setPauseVisible(boolean visible) {
        pause.setVisible(visible);
        if (visible) {
            pause.toFront();   // see showObjective
        }
    }

    // The result of a SCORING run: the same panel, with the Meow Point breakdown under the message.
    //
    // Built from the manager's own numbers rather than from `buildScorecard()`. That string exists and
    // is correct, but it is a fixed-width terminal card -- column-aligned with %-20s and printf padding
    // -- and the skin's font is proportional, so pasting it into a Label produces ragged columns that
    // look like a layout bug. The rule labels and awards live on `MeowPointRule`, so a Scene2D table
    // reads the same source the text does and neither can drift from the other.
    //
    // `points` may be null, which is simply an ordinary level.
    public void showOutcome(boolean won, String message,
                            models.game.scoring.MeowPointManager points) {
        buildScorecard(points);
        showOutcome(won, message);
    }

    // Rules that never fired are left out, exactly as the terminal card leaves them out: the panel shows
    // what the player DID, not a checklist of what they missed.
    private void buildScorecard(models.game.scoring.MeowPointManager points) {
        scorecard.clearChildren();
        if (points == null) {
            return;
        }
        for (models.game.scoring.MeowPointRule rule : models.game.scoring.MeowPointRule.values()) {
            int hits = points.getHits(rule);
            if (hits == 0) {
                continue;
            }
            scoreRow(rule.getLabel() + "  x" + hits, "+" + points.getPoints(rule), false);
        }
        if (scorecard.getChildren().size == 0) {
            scoreRow("No bonuses earned this run.", "", false);
        }
        scoreRow("TOTAL", String.valueOf(points.getTotal()), true);
        scoreRow("Zombies destroyed", String.valueOf(points.getKills()), false);
    }

    // Meow Points wear the scoring game's gold, so the total reads as a score rather than as more body
    // text. Everything else stays the panel's ordinary colour.
    private static final Color SCORE_TOTAL = new Color(1f, 0.86f, 0.32f, 1f);

    private void scoreRow(String name, String value, boolean total) {
        Label left = MenuStyles.label(skin, name, MenuStyles.TEXT);
        Label right = MenuStyles.label(skin, value, MenuStyles.TEXT);
        if (total) {
            left.setColor(SCORE_TOTAL);
            right.setColor(SCORE_TOTAL);
        }
        scorecard.add(left).left().expandX();
        scorecard.add(right).right();
        scorecard.row();
    }

    // The result. The two sentences are the model's own, taken from the events GameEngine already
    // emits, so the overlay and the toast that preceded it cannot disagree about what happened.
    public void showOutcome(boolean won, String message) {
        outcomeTitle.setText(won ? "Level Complete!" : "The Zombies Ate Your Brains");
        outcomeBody.setText(message);
        // Losing offers the level back. Same restart the pause menu runs -- a fresh session on the same
        // level with the same loadout -- so a failed attempt costs a click rather than a trip out to the
        // map and back through seed selection.
        if (retryCell != null) {
            retryButton.setVisible(!won);
            retryCell.height(won ? 0f : BUTTON_HEIGHT).padBottom(won ? 0f : 8f);
        }
        boolean scored = scorecard.getChildren().size > 0;
        if (scoreCell != null) {
            // Sized to whatever the card came out as -- the number of rows depends on how many rules
            // actually paid, and a fixed height would either clip a full card or leave a gap under a
            // thin one. Collapsed to nothing when there is no card at all.
            scoreCell.height(scored ? scorecard.getPrefHeight() : 0f);
            scoreCell.padBottom(scored ? 16f : 0f);
        }
        if (outcomeArt != null) {
            // Losing gets the picture; winning does not. A half-eaten brain over "Level Complete!"
            // would be congratulating the wrong side.
            //
            // Nor does a scored run, win or lose: the brain and the breakdown together are 400 units of
            // panel, and the thing the player came back to read is the score.
            outcomeArt.setVisible(!won && !scored);
            // Hidden actors still hold their cell, so the cell is collapsed as well -- otherwise a win
            // panel is a title floating under 235 units of empty frame.
            //
            // Through the saved cell, NOT outcome.getCell(outcomeArt): `outcome` is the full-screen
            // dimmed layer and the art lives in the framed box INSIDE it, so getCell returned null and
            // every loss threw an NPE out of the render loop -- the one moment the panel exists for.
            if (artCell != null) {
                boolean showArt = outcomeArt.isVisible();
                artCell.height(showArt ? GAME_OVER_WIDTH * 383f / 586f : 0f);
                artCell.padBottom(showArt ? 8f : 0f);
            }
        }
        outcome.invalidateHierarchy();
        outcome.setVisible(true);
        outcome.toFront();   // see showObjective
    }

    public boolean isOutcomeVisible() {
        return outcome.isVisible();
    }

    // True while any of the three is up, so the lawn can stop taking clicks behind them.
    public boolean isAnyVisible() {
        return objective.isVisible() || pause.isVisible() || outcome.isVisible();
    }
}
