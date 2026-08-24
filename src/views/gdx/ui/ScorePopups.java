package views.gdx.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.utils.viewport.Viewport;
import models.game.GameSession;
import models.game.gamemodes.ScoringMode;
import models.game.scoring.MeowPointRule;
import views.gdx.map.LawnGeometry;
import views.gdx.render.DeathEffects;

import java.util.EnumMap;
import java.util.Map;

// The scoring game's floating Meow Point awards: "+30 Speed Kill" rising off the zombie that earned it.
//
// ## Why this is watched rather than listened for
//
// `MeowPointManager` keeps a per-rule tally and raises no event, and it should not start raising one.
// Two of the five rules are settlement rules read once at the end, and the other three fire inside
// `CombatSystem`'s death loop -- a burst of Meow Point sentences during a heavy wave would be dozens a
// second in a stream the terminal build also prints, for something that is purely a flourish.
//
// So this diffs the tally instead, exactly as `DamageFlash` watches health and `ZombieRenderer` watches
// the armour stack: a hit count that went up IS the rule having fired. Nothing reaches the model, and a
// board with no scoring mode never allocates anything here.
//
// ## Why the popups live on the HUD's Stage and not on the lawn
//
// Nothing in `views.gdx.render` draws text -- every renderer there is sprites and shapes -- so a
// world-space label would mean putting a font, a glyph layout and a fade curve into the entity pass.
// Scene2D already has all three, and a popup IS interface: it is a number telling the player what they
// just earned, not part of the scene. The lawn position is projected through the two viewports so it
// still lands on the zombie, and it survives letterboxing and the camera's pan because the projection
// is asked rather than assumed.
public final class ScorePopups {

    // How far a popup rises, in stage units, over its life.
    private static final float RISE = 70f;
    private static final float LIFETIME = 1.25f;
    private static final float FADE_IN = 0.12f;

    // Meow Points are the scoring game's own currency, so the popups wear its colour rather than the
    // white every other piece of HUD text uses -- there is no mistaking one for a damage number.
    private static final Color AWARD = new Color(1f, 0.86f, 0.32f, 1f);

    private final Stage stage;
    private final com.badlogic.gdx.scenes.scene2d.ui.Skin skin;
    private final Viewport lawnViewport;

    // The tally as of last frame. Filled on the first watch, so a mode already part-way through a run
    // (a restart, or a screen rebuilt behind an overlay) does not fire five popups at once for points
    // that were earned before this object existed.
    private final Map<MeowPointRule, Integer> seen = new EnumMap<>(MeowPointRule.class);
    private boolean primed;

    // Reused across frames: this projects a point every time a rule fires, and Vector2 is otherwise a
    // fresh allocation per popup.
    private final Vector2 scratch = new Vector2();

    public ScorePopups(Stage stage, com.badlogic.gdx.scenes.scene2d.ui.Skin skin,
                       Viewport lawnViewport) {
        this.stage = stage;
        this.skin = skin;
        this.lawnViewport = lawnViewport;
    }

    // Called once a frame, straight after the model has ticked, so the death that earned the points and
    // the corpse marking where it happened arrive together.
    //
    // Silent on every board that is not the scoring game, which is what lets GameScreen call it
    // unconditionally.
    public void watch(GameSession session, DeathEffects deaths, LawnGeometry lawn) {
        if (session == null || !(session.getMode() instanceof ScoringMode scoring)) {
            return;
        }
        models.game.scoring.MeowPointManager points = scoring.getMeowPoints();
        int raised = 0;
        for (MeowPointRule rule : MeowPointRule.values()) {
            int now = points.getHits(rule);
            Integer before = seen.put(rule, now);
            if (!primed || before == null || now <= before) {
                continue;
            }
            // Only the kill-driven rules get a popup. The two settlement rules fire once, at the moment
            // the level ends and the result panel is going up in front of the board -- a number rising
            // off an empty lawn behind it would be read as something that had just happened.
            if (rule == MeowPointRule.SUN_HOARDER || rule == MeowPointRule.FLAWLESS_DEFENSE) {
                continue;
            }
            int times = now - before;
            // Stacked, because rules do not fire one at a time: a Cherry Bomb landing on a row of
            // zombies wins the simultaneous-kill and the one-shot award on the SAME kill, so both
            // popups leave from the same tile in the same frame. Drawn at the same point they overlap
            // into unreadable mush -- which is exactly what the first run of this produced.
            pop("+" + rule.getAward() * times + "  " + rule.getLabel(),
                    deaths.lastDeathX(), lawn.centerY(deaths.lastDeathRow()), raised++);
        }
        primed = true;
    }

    // Vertical gap between two popups raised on the same frame, in stage units. A little more than a
    // line of the skin's body text, so they read as a short list rather than as one smeared label.
    private static final float STACK_STEP = 24f;

    // Raises one popup at a lawn position. Public so a harness can fire one without a kill.
    public void pop(String text, float worldX, float worldY) {
        pop(text, worldX, worldY, 0);
    }

    private void pop(String text, float worldX, float worldY, int stackIndex) {
        Label label = new Label(text, skin);
        label.setColor(AWARD);
        label.pack();

        Vector2 at = toStage(worldX, worldY);
        // Centred on the point rather than starting at it: a popup anchored by its left edge drifts
        // right as the text gets longer, so "Simultaneous Kill" would sit off the zombie entirely.
        label.setPosition(at.x - label.getWidth() / 2f, at.y + stackIndex * STACK_STEP);

        label.addAction(Actions.sequence(
                Actions.alpha(0f),
                Actions.parallel(
                        Actions.fadeIn(FADE_IN),
                        Actions.moveBy(0f, RISE, LIFETIME, com.badlogic.gdx.math.Interpolation.pow2Out)),
                Actions.fadeOut(LIFETIME - FADE_IN),
                Actions.removeActor()));
        stage.addActor(label);
    }

    // Lawn world coordinates -> HUD stage coordinates, via the screen.
    //
    // Two viewports have to be crossed and there is no shortcut: the lawn is a 1365-wide slice of a
    // 1975-wide background that PANS, and the HUD is a fixed 1280x720 FitViewport. Projecting to the
    // screen and unprojecting back is what keeps a popup welded to the zombie through a resize, a
    // letterbox and a camera move.
    private Vector2 toStage(float worldX, float worldY) {
        scratch.set(worldX, worldY);
        lawnViewport.project(scratch);
        // project() gives y-up screen coordinates and unproject() wants y-down. Flipping here rather
        // than trusting them to cancel: they do not, and the popup comes out mirrored about the middle
        // of the window, which looks plausible near the centre and wrong everywhere else.
        scratch.y = com.badlogic.gdx.Gdx.graphics.getHeight() - scratch.y;
        return stage.getViewport().unproject(scratch);
    }

}
