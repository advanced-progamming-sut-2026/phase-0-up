package views.gdx.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import views.gdx.core.Assets;
import views.gdx.core.PvZGame;
import views.gdx.sprite.EntitySprite;
import views.gdx.sprite.SpriteRegistry;

import java.util.List;

// Throwaway verification screen for Phase 0: proves the asset chain end to end before anything is
// built on top of it. Deleted once the real ScreenManager and GameScreen land.
//
// It answers three questions that everything downstream depends on:
//   1. does the pvz-skin Scene2D skin load (TenPatch drawables + FreeType fonts included)?
//   2. does TextureBank resolve a region out of a 780-page atlas set?
//   3. does libPVZ actually animate a .PAM, for both a plant and a zombie?
public final class SpriteSmokeScreen extends ScreenAdapter {

    // One plant and one zombie per row, chosen to cover the interesting cases: a plain normalised
    // match (Peashooter), an override (Twin Sunflower -> SUNFLOWER_TWIN), an armor zombie that has no
    // animation of its own (ZombieArmor1), and one known to be missing entirely (Rotobaga).
    private static final List<String> DEFAULT_PLANTS =
            List.of("Peashooter", "Sunflower", "Wall-nut", "Snow Pea", "Twin Sunflower", "Rotobaga");
    private static final List<String> DEFAULT_ZOMBIES =
            List.of("ZombieDefault", "ZombieArmor1", "ZombieGargantuar", "ZombieRa",
                    "ZombieDarkJuggler", "ZombieNewspaper");

    // Also usable as a general-purpose animation viewer, which is the quickest way to check a single
    // .PAM without leaving the project:
    //
    //   gradlew runGui -Dpvz.screen=sprites -Dpvz.preview=ZombieRa,Gargantuar
    //   gradlew runGui -Dpvz.screen=sprites -Dpvz.preview=Peashooter -Dpvz.previewClip=attack
    //
    // Names go through the same SpriteRegistry the game uses, so an entity name from plants.json or a
    // raw animation name from animations.json both work -- and a name that resolves to nothing shows
    // up here exactly as it would in game.
    private final List<String> topRow;
    private final List<String> bottomRow;
    private final String forcedClip = System.getProperty("pvz.previewClip");

    private final SpriteRegistry sprites;

    private final SpriteBatch batch = new SpriteBatch();
    private final Viewport viewport =
            new FitViewport(PvZGame.VIRTUAL_WIDTH, PvZGame.VIRTUAL_HEIGHT);
    private final Label caption;

    private float stateTime;

    public SpriteSmokeScreen(views.gdx.core.GdxContext context) {
        Assets assets = context.assets();
        this.sprites = context.sprites();

        String requested = System.getProperty("pvz.preview");
        if (requested != null && !requested.isBlank()) {
            // Everything on one row when the caller named the subjects; splitting them into
            // "plants" and "zombies" would be a guess.
            this.topRow = List.of(requested.trim().split("\\s*,\\s*"));
            this.bottomRow = List.of();
        } else {
            this.topRow = DEFAULT_PLANTS;
            this.bottomRow = DEFAULT_ZOMBIES;
        }

        String title = requested != null && !requested.isBlank()
                ? "preview: " + requested + (forcedClip != null ? "  [" + forcedClip + "]" : "")
                : "libPVZ smoke test -- plants (top) / zombies (bottom)";
        this.caption = new Label(title, assets.skin());

        warm();

        // Also exercises the toast stack, so Phase 0 ends with both halves of the view layer proven.
        context.toasts().success("Assets loaded: " + sprites.clipsOf("Peashooter").size()
                + " Peashooter clips");
        if (!sprites.unresolvedNames().isEmpty()) {
            context.toasts().error("No animation for: " + String.join(", ", sprites.unresolvedNames()));
        }
    }

    // Resolving every sprite up front turns "which of these actually exist?" into one log line instead
    // of a discovery spread across frames.
    private void warm() {
        List<String> all = new java.util.ArrayList<>(topRow);
        all.addAll(bottomRow);
        for (String name : all) {
            sprites.get(name);
            // The clip list is the useful part when inspecting an unfamiliar animation: it tells you
            // what -Dpvz.previewClip values are legal for it.
            EntitySprite sp = sprites.get(name);
            StringBuilder durations = new StringBuilder();
            for (String c : sprites.clipsOf(name)) {
                durations.append(c).append('=').append(sp.clipDuration(c)).append("s ");
            }
            Gdx.app.log("SpriteSmoke", name + " animated=" + sprites.hasAnimation(name)
                    + " durations=[" + durations.toString().trim() + "]");
        }
        if (!sprites.unresolvedNames().isEmpty()) {
            Gdx.app.log("SpriteSmoke", "fell back to still images: " + sprites.unresolvedNames());
        }
    }

    @Override
    public void render(float delta) {
        stateTime += delta;

        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        drawRow(topRow, bottomRow.isEmpty() ? 280f : 430f, "idle");
        drawRow(bottomRow, 140f, "walk");
        batch.end();

        // Drawn through the skin's own batch state so a broken font shows up here rather than silently.
        batch.begin();
        caption.setPosition(24f, PvZGame.VIRTUAL_HEIGHT - 48f);
        caption.draw(batch, 1f);
        batch.end();
    }

    // -Dpvz.previewStagger=1 draws the FIRST subject five times across the screen at stateTimes
    // 0.0, 0.4, 0.8, 1.2, 1.6s within a single frame. If the five poses differ, stateTime drives the
    // animation. If they are identical, the player is holding the pose itself and ignoring the
    // argument -- which would mean no arithmetic on stateTime can ever make a clip cycle.
    private final boolean stagger = "1".equals(System.getProperty("pvz.previewStagger"));

    private void drawRow(List<String> names, float y, String preferredClip) {
        if (names.isEmpty()) {
            return;
        }
        if (stagger) {
            EntitySprite sprite = sprites.get(names.get(0));
            String wanted = forcedClip != null && !forcedClip.isBlank() ? forcedClip : preferredClip;
            String clip = sprite.hasClip(wanted) ? wanted : "idle";
            for (int i = 0; i < 5; i++) {
                sprite.draw(batch, clip, i * 0.4f, 180f + i * 230f, y, true);
            }
            return;
        }
        float spacing = PvZGame.VIRTUAL_WIDTH / (names.size() + 1f);
        for (int i = 0; i < names.size(); i++) {
            EntitySprite sprite = sprites.get(names.get(i));
            String wanted = forcedClip != null && !forcedClip.isBlank() ? forcedClip : preferredClip;
            // Not every entity defines the clip we would like -- fall back rather than draw nothing.
            String clip = sprite.hasClip(wanted) ? wanted : "idle";
            // Same loop/clamp policy the lawn uses, so the preview shows what the game shows.
            sprite.draw(batch, clip, views.gdx.sprite.ClipMap.sample(sprite, clip, stateTime),
                    spacing * (i + 1), y, true);
        }
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    @Override
    public void dispose() {
        batch.dispose();
        // assets is owned by PvZGame -- deliberately not disposed here.
    }
}
