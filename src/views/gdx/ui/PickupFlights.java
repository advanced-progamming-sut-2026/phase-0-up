package views.gdx.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.Viewport;
import views.gdx.map.LawnGeometry;
import views.gdx.render.DeathEffects;

import java.util.regex.Pattern;

// A dropped reward, flying from the zombie that was carrying it to the counter that now holds it.
//
// ## Why an animation at all
//
// Every one of these drops is already auto-collected -- `CombatSystem` credits the plant food, the
// coins, the gem or the pot on the tick the zombie dies and there has never been anything to click.
// That is the right rule and it is not changing here. The problem is that it happens SILENTLY: the only
// evidence was a toast in the corner reading "A zombie dropeed a coin", which arrives among spawn
// notices during a wave and is routinely missed. So the reward is given a path the eye can follow from
// where it was earned to where it landed, which is the whole content of this class.
//
// ## Why it is watched off the narration
//
// The model already says all four of these sentences, in `CombatSystem.reportZombieDeath`, and the
// terminal build prints them. Adding an event for the view's benefit would be a second way of saying
// the same thing that could drift from the first -- the trade this codebase has refused every time it
// has come up. So this is the eighth consumer of `GameScreen.onModelEvent`, and it ignores everything
// that is not its own sentence, exactly as the explosions and the weather do.
//
// ## Where the flight starts
//
// The drop sentences carry no coordinates, and the zombie is gone by the time they are read --
// `processDeaths` removes it inside the same tick. `DeathEffects` is the one thing that still knows
// where the body fell, and the death line is queued immediately before the drop lines it caused, so
// reading its last death when a drop arrives is reading the right zombie. That is the same seam
// `ScorePopups` uses to place a Meow Point award, and for the same reason.
public final class PickupFlights {

    // "The glowing zombie dropeed a plant food; ..." and "A zombie dropeed a coin; ...".
    //
    // Matched loosely on purpose -- the spec's "dropeed" typo is verbatim in the model and pinned
    // there, and a pattern that insisted on the exact sentence would break the moment its wording was
    // tidied, silently, with no animation and no error.
    private static final Pattern PLANT_FOOD = Pattern.compile(".*dropeed a plant food.*");
    private static final Pattern LOOT = Pattern.compile(".*dropeed a (coin|gem|pot).*");

    // How long the icon takes to reach its counter. Fast enough not to lag behind the fight, slow
    // enough that the eye can follow it across a busy board.
    private static final float FLIGHT_SECONDS = 0.62f;
    private static final float POP_SECONDS = 0.16f;
    private static final float ICON_SIZE = 34f;
    // The pop before the flight: the icon swells past its size and settles, so the reward reads as
    // having been knocked loose rather than as having faded in.
    private static final float POP_SCALE = 1.45f;

    private static final float BOUNCE_SCALE = 1.35f;
    private static final float BOUNCE_UP = 0.09f;
    private static final float BOUNCE_DOWN = 0.16f;

    // The floating text, once the counter has absorbed it.
    private static final float TEXT_RISE = 30f;
    private static final float TEXT_SECONDS = 1f;
    private static final Color TEXT_COLOUR = new Color(1f, 0.94f, 0.55f, 1f);

    private final Stage stage;
    private final Skin skin;
    private final Viewport lawnViewport;
    private final GameHud hud;
    private final LawnGeometry lawn;

    // Reused: a wave can drop several rewards in one frame and each would otherwise allocate.
    private final Vector2 scratch = new Vector2();

    private DeathEffects deaths;

    public PickupFlights(Stage stage, Skin skin, Viewport lawnViewport, GameHud hud,
                         LawnGeometry lawn) {
        this.stage = stage;
        this.skin = skin;
        this.lawnViewport = lawnViewport;
        this.hud = hud;
        this.lawn = lawn;
    }

    // Handed in rather than reached for, so the screen keeps the single event fan-out it documents.
    public void setDeaths(DeathEffects deaths) {
        this.deaths = deaths;
    }

    // One model sentence. Silent on everything that is not a drop.
    public void onEvent(String message) {
        if (message == null) {
            return;
        }
        if (PLANT_FOOD.matcher(message).matches()) {
            launch(PickupKind.PLANT_FOOD);
            return;
        }
        java.util.regex.Matcher loot = LOOT.matcher(message);
        if (!loot.matches()) {
            return;
        }
        switch (loot.group(1)) {
            case "gem" -> launch(PickupKind.GEM);
            case "pot" -> launch(PickupKind.POT);
            default -> launch(PickupKind.COIN);
        }
    }

    private void launch(PickupKind kind) {
        Drawable art = MenuStyles.drawable(skin, kind.iconId());
        Actor target = hud.counterTarget(kind);
        if (target == null || target.getStage() == null) {
            return;
        }
        Vector2 to = centreOf(target);

        // Falls back to the counter itself when the corpse's position is unknown -- a drop credited by
        // something other than a normal death (the nuke, a mode clearing the board) still has to bounce
        // the counter and say what was earned. A flight from nowhere is the one part that is skipped.
        Vector2 from = deathPoint();
        if (art == null || from == null) {
            absorb(kind, to);
            return;
        }

        Image icon = new Image(art);
        icon.setSize(ICON_SIZE, ICON_SIZE);
        icon.setOrigin(Align.center);
        icon.setPosition(from.x - ICON_SIZE / 2f, from.y - ICON_SIZE / 2f);

        // moveTo takes the actor's BOTTOM-LEFT, so the target is offset by half the icon or every
        // reward lands up and to the right of the counter it is supposed to hit.
        float toX = to.x - ICON_SIZE / 2f;
        float toY = to.y - ICON_SIZE / 2f;

        icon.addAction(Actions.sequence(
                Actions.scaleTo(0.4f, 0.4f),
                Actions.scaleTo(POP_SCALE, POP_SCALE, POP_SECONDS, Interpolation.swingOut),
                Actions.parallel(
                        Actions.moveTo(toX, toY, FLIGHT_SECONDS, Interpolation.swing),
                        Actions.scaleTo(0.85f, 0.85f, FLIGHT_SECONDS)),
                // absorb BEFORE removeActor, and that order is load-bearing: a removed actor stops
                // being acted, so a run() queued after removeActor() never executes. It compiles, it
                // reads correctly, and the icon simply reaches the counter and nothing happens -- no
                // bounce, no text. Cost a screenshot round to find.
                Actions.run(() -> absorb(kind, to)),
                Actions.removeActor()));
        stage.addActor(icon);
        icon.toFront();
    }

    // The counter has it now: bounce the thing that absorbed it and say what it was.
    private void absorb(PickupKind kind, Vector2 at) {
        Actor target = hud.counterTarget(kind);
        if (target != null) {
            // clearActions first: two coins landing a frame apart would otherwise leave two scaleTo
            // tweens fighting over the same counter, and it settles wherever they happen to cross.
            target.clearActions();
            target.setOrigin(Align.center);
            target.addAction(Actions.sequence(
                    Actions.scaleTo(BOUNCE_SCALE, BOUNCE_SCALE, BOUNCE_UP, Interpolation.pow2Out),
                    Actions.scaleTo(1f, 1f, BOUNCE_DOWN, Interpolation.swingOut)));
        }
        floatText(kind.label(), at);
    }

    // Vertical gap between two labels raised in the same frame. A wave can drop several rewards at
    // once and they land on counters that sit beside each other, so without this they draw as one
    // smeared line -- the same lesson ScorePopups records for its awards.
    private static final float STACK_STEP = 22f;
    private float lastTextTime = -1f;
    private int stacked;

    private void floatText(String text, Vector2 at) {
        // The skin's OUTLINED face, not its body text. These labels rise across the seed bank, which is
        // a row of busy, mostly-light card art -- plain text on top of it is legible against one card
        // and invisible against the next. An outline is readable over anything, which is what it is for.
        Label label = MenuStyles.label(skin, text, MenuStyles.HEADING);
        label.setColor(TEXT_COLOUR);
        label.pack();
        // Started BELOW the counter, not above it.
        //
        // Every counter this can land on sits in the top row of the HUD, within about thirty units of
        // the stage's ceiling -- so a label that starts above one and then rises another thirty is
        // off the top of the screen for its whole life. It was: the flight and the bounce both worked
        // and the text was simply never visible. Starting a line below means the rise carries it up
        // PAST the counter, which reads the same way and stays on screen.
        float now = com.badlogic.gdx.Gdx.graphics == null ? 0f : (float) com.badlogic.gdx.Gdx.graphics.getFrameId();
        stacked = (now == lastTextTime) ? stacked + 1 : 0;
        lastTextTime = now;
        label.setPosition(at.x - label.getWidth() / 2f,
                at.y - ICON_SIZE - label.getHeight() - stacked * STACK_STEP);
        label.addAction(Actions.sequence(
                Actions.parallel(
                        Actions.moveBy(0f, TEXT_RISE, TEXT_SECONDS),
                        Actions.fadeOut(TEXT_SECONDS)),
                Actions.removeActor()));
        stage.addActor(label);
        label.toFront();
    }

    // Where the body fell, as a point on the HUD's stage. Null only before anything has been drawn --
    // DeathEffects answers with the middle of the board rather than a sentinel, so a drop credited
    // without a normal death (the nuke clearing the lawn) still flies from somewhere plausible.
    private Vector2 deathPoint() {
        if (deaths == null) {
            return null;
        }
        return toStage(deaths.lastDeathX(), lawn.centerY(deaths.lastDeathRow()));
    }

    private Vector2 centreOf(Actor actor) {
        return actor.localToStageCoordinates(
                new Vector2(actor.getWidth() / 2f, actor.getHeight() / 2f));
    }

    // Lawn world coordinates -> HUD stage coordinates, via the screen. Two viewports and no shortcut:
    // the lawn is a slice of a wider background that PANS and the HUD is a fixed 1280x720 FitViewport,
    // so projecting out and unprojecting back is what keeps the launch point on the zombie through a
    // resize, a letterbox and a camera move. Same crossing ScorePopups makes.
    private Vector2 toStage(float worldX, float worldY) {
        scratch.set(worldX, worldY);
        lawnViewport.project(scratch);
        // project() gives y-up screen coordinates and unproject() wants y-down. Flipping here rather
        // than trusting them to cancel: they do not, and the flight comes out mirrored about the middle
        // of the window -- plausible near the centre and wrong everywhere else. Learned the hard way in
        // ScorePopups, which crosses the same two viewports.
        scratch.y = com.badlogic.gdx.Gdx.graphics.getHeight() - scratch.y;
        return new Vector2(stage.getViewport().unproject(scratch));
    }
}
