package views.gdx.ui;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import models.social.Reaction;
import views.gdx.core.Assets;
import views.gdx.sprite.SpriteRegistry;

import java.util.ArrayDeque;
import java.util.Deque;

// What your opponent just said, shown for a few seconds.
//
// The opposite corner from the ReactionBar, deliberately: yours goes out from the bottom right and
// theirs arrives at the top right, so at a glance it is obvious which of the two you are looking at.
// Touchable.disabled throughout -- it is a notice, and a notice that eats clicks over a live lawn is
// a notice that loses you the match.
//
// ## One at a time, queued
//
// A player who mashes the bar cannot wallpaper the other person's screen: reactions are shown one
// after another, each for its own dwell, and the queue is capped. Beyond that cap the OLDEST waiting
// one is dropped rather than the newest -- a spammer's backlog is not worth showing, and what somebody
// sent most recently is the thing worth reading. The server also rate-limits at the source; this is
// the half that survives a modified client.
public final class ReactionPopup {

    private static final com.badlogic.gdx.graphics.Color BUBBLE =
            new com.badlogic.gdx.graphics.Color(0.11f, 0.13f, 0.17f, 0.94f);

    private static final float DWELL_SECONDS = 3f;
    private static final int MAX_QUEUED = 3;
    private static final float ICON = 64f;
    private static final float WIDTH = 260f;

    private final Assets assets;
    private final SpriteRegistry sprites;

    private final Table root = new Table();
    private final Table bubble = new Table();
    private final Label who;
    private final Label what;
    private final Table artSlot = new Table();
    private com.badlogic.gdx.scenes.scene2d.ui.Cell<?> artCell;

    private final Deque<Shown> pending = new ArrayDeque<>();
    private float remaining;

    private record Shown(String from, Reaction reaction) { }

    public ReactionPopup(Assets assets, SpriteRegistry sprites, Stage stage) {
        this.assets = assets;
        this.sprites = sprites;

        who = new Label("", assets.skin());
        who.setAlignment(Align.center);
        who.setFontScale(0.8f);
        what = new Label("", assets.skin());
        what.setAlignment(Align.center);
        what.setWrap(true);

        // Solid, for the reason ReactionBar's panel is: the HUD's 3-slice strip washes out at this
        // size and a message from the other player has to be readable over whatever the lawn is doing.
        bubble.setBackground(assets.solid(BUBBLE));
        bubble.pad(10f);
        bubble.add(who).width(WIDTH).row();
        artCell = bubble.add(artSlot).size(ICON).padTop(4f);
        bubble.row();
        bubble.add(what).width(WIDTH).padTop(4f);
        bubble.setVisible(false);

        root.setFillParent(true);
        root.setTouchable(Touchable.disabled);
        // Clear of the toast band along the top. Both are notices and both arrive unbidden, so when a
        // reaction and a toast land together they would otherwise print over each other -- which is
        // exactly what happened the first time an opponent taunted and then dropped out.
        root.top().right().pad(96f, 12f, 12f, 12f);
        root.add(bubble);
        stage.addActor(root);
    }

    public void show(String from, Reaction reaction) {
        if (reaction == null) {
            return;
        }
        pending.addLast(new Shown(from == null ? "Your opponent" : from, reaction));
        while (pending.size() > MAX_QUEUED) {
            pending.pollFirst();
        }
        if (!bubble.isVisible()) {
            next();
        }
    }

    // Driven from GameScreen's render with the REAL frame delta, not the animation delta: a reaction
    // is a message from another person and it must not freeze because this player paused.
    public void update(float delta) {
        if (!bubble.isVisible()) {
            return;
        }
        remaining -= delta;
        if (remaining <= 0f) {
            next();
        }
    }

    private void next() {
        Shown shown = pending.pollFirst();
        if (shown == null) {
            bubble.setVisible(false);
            return;
        }
        who.setText(shown.from() + " says:");
        what.setText(ReactionArt.caption(shown.reaction()));
        artSlot.clearChildren();
        Actor art = artFor(shown.reaction());
        if (art != null) {
            artSlot.add(art).size(ICON);
        }
        // Collapsed when there is nothing to draw. A text reaction has no picture, and a hidden actor
        // still holds its cell -- leaving it sized would open a band of empty frame above every line
        // of text, which is what the first version did. Same trap GameOverlays documents.
        artCell.size(art == null ? 0f : ICON).padTop(art == null ? 0f : 4f);
        bubble.invalidateHierarchy();
        remaining = DWELL_SECONDS;
        bubble.setVisible(true);
    }

    private Actor artFor(Reaction reaction) {
        String region = ReactionArt.region(reaction);
        if (region != null) {
            try {
                Image image = new Image(new TextureRegionDrawable(assets.region(region)));
                image.setScaling(com.badlogic.gdx.utils.Scaling.fit);
                return image;
            } catch (RuntimeException missing) {
                return null;
            }
        }
        String animation = ReactionArt.animation(reaction);
        if (animation == null) {
            return null;
        }
        // The bonus half of the feature: a sticker is a real animation rather than a still, drawn
        // through EntitySprite -- the only sanctioned route to the PAM runtime, and the one that
        // degrades to a frozen frame instead of taking the renderer down when a clip is missing.
        EntityIcon icon = new EntityIcon(sprites.get(animation));
        return icon.hasArt() ? icon : null;
    }

    public void dispose() {
        root.remove();
    }
}
