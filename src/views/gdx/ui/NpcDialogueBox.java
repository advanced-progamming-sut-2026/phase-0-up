package views.gdx.ui;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import views.gdx.core.Assets;
import views.gdx.sprite.EntitySprite;
import views.gdx.sprite.SpriteRegistry;

// Crazy Dave, Penny and Dr. Zomboss, saying the things the model already says.
//
// ## The art was the open question, and the dump answers it
//
// The blueprint named this class in Phase 2 and nothing was ever built, because it needed an art
// decision first. It does not: PopCap ships a NARRATION ICON set for exactly this, and both halves of
// it are in the dump at a 350x350 canvas.
//
//   DAVEWINNIE_NARRATIONICONS   clip `dave`    -- Crazy Dave in a framed blue roundel
//                               clip `winnie`  -- the Winnebago in a framed pink roundel
//   NARRATIONICONS_ZOMBOSS      clip `anim_idle` -- Zomboss in a framed green roundel
//
// **"Winnie" is the Winnebago, not a person**, and that is the icon Penny gets. It is not a
// substitution: Penny IS the van in PvZ2 -- she is its AI and it is her body -- and this is the icon
// PopCap's own narration set uses for her. Worth saying out loud because the clip name gives no hint of
// it, and the first guess from the name alone is that `winnie` is a second character.
//
// **Zomboss must be played on `anim_idle`.** Its `anim_enter` opens on a white flash and shows no
// portrait at all for most of a second, so a box that played the clip its name suggests would look
// broken every time Zomboss spoke. Same trap as the Imp's `fly` and the `particles` pose: the clip's
// name says nothing about what is in it.
//
// ## Where the words come from
//
// Nothing here is written. Every line is a sentence the model already emits and the terminal build
// already prints -- so the two front ends cannot disagree about what was said, and no player-facing
// text was invented for a cosmetic feature. See views.gdx.ui.NpcLines for the routing.
public final class NpcDialogueBox {

    // Who is speaking, and the art each one wears.
    public enum Speaker {
        DAVE("DAVEWINNIE_NARRATIONICONS", "dave", "Crazy Dave"),
        PENNY("DAVEWINNIE_NARRATIONICONS", "winnie", "Penny"),
        ZOMBOSS("NARRATIONICONS_ZOMBOSS", "anim_idle", "Dr. Zomboss");

        private final String animation;
        private final String clip;
        private final String name;

        Speaker(String animation, String clip, String name) {
            this.animation = animation;
            this.clip = clip;
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    // Long enough to read four lines of a mode's rules without hurrying, short enough that it is gone
    // before the first zombie matters. A click dismisses it early.
    private static final float DWELL = 9f;
    private static final float FADE = 0.3f;

    private final SpriteRegistry sprites;
    private final Table root;
    private final Portrait portrait;
    private final Label speakerName;
    private final Label speech;

    public NpcDialogueBox(Assets assets, SpriteRegistry sprites, Stage stage) {
        this.sprites = sprites;
        this.portrait = new Portrait();

        speakerName = MenuStyles.label(assets.skin(), "", MenuStyles.TITLE);
        speakerName.setFontScale(0.55f);
        speech = MenuStyles.label(assets.skin(), "", MenuStyles.TEXT);
        speech.setWrap(true);
        speech.setAlignment(Align.left);

        Table box = new Table();
        com.badlogic.gdx.scenes.scene2d.utils.Drawable frame =
                MenuStyles.drawable(assets.skin(), MenuStyles.PANEL_BORDER);
        if (frame != null) {
            // The same opaque fill the result panel tucks under its border. The frame is translucent
            // through the middle, and this box sits over a lit lawn where zombies read straight through
            // the text -- see GameOverlays.
            box.setBackground(MenuStyles.layered(MenuStyles.panelFill(assets.skin()), frame, 14f));
        }
        // Generous at the bottom: the frame is an ornate border about twenty units thick, so a box padded
        // evenly puts its last wrapped line UNDER the gold edge.
        box.pad(18f, 30f, 26f, 30f);
        box.add(portrait).size(96f, 96f).padRight(14f);

        Table words = new Table();
        words.add(speakerName).left().row();
        words.add(speech).width(430f).left().padTop(2f);
        box.add(words).top();

        root = new Table();
        root.setFillParent(true);
        // Bottom-centre, clear of everything else the HUD puts on screen: the seed bank is top-left, the
        // toasts top-right, the upgrade panel bottom-right, and the wave meter is 440 wide in the
        // middle of the floor -- so this sits ABOVE the meter rather than beside it.
        root.bottom().padBottom(78f);
        root.add(box);
        root.setVisible(false);
        root.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.enabled);
        root.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                hide();
            }
        });
        stage.addActor(root);
    }

    // Puts a line up. Replaces whatever was there rather than queueing: two of these can only collide
    // when two things happen at once, and the newer one is the one the player is looking at.
    public void say(Speaker speaker, String line) {
        if (speaker == null || line == null || line.isBlank()) {
            return;
        }
        portrait.show(sprites.get(speaker.animation), speaker.clip);
        speakerName.setText(speaker.getName());
        speech.setText(line.trim());

        // The wrapped body changes height with every line, so the box has to be re-measured rather than
        // keeping whatever the previous speaker sized it to.
        root.invalidateHierarchy();
        root.clearActions();
        root.setVisible(true);
        root.getColor().a = 1f;
        // In front of anything added to the Stage after it -- score popups, most obviously. Same reason
        // GameOverlays raises its panels.
        root.toFront();
        root.addAction(Actions.sequence(
                Actions.delay(DWELL),
                Actions.fadeOut(FADE),
                Actions.visible(false)));
    }

    public void hide() {
        root.clearActions();
        root.setVisible(false);
        root.getColor().a = 1f;
    }

    public boolean isVisible() {
        return root.isVisible();
    }

    // The speaker's icon, fitted to its cell.
    //
    // Not LiveEntityActor, which stands an entity's FEET on a patch of turf and picks its own resting
    // clip -- both wrong for a roundel that has no feet and whose clip is the thing that identifies the
    // character. Small enough to live here rather than becoming a fourth almost-identical icon class.
    private static final class Portrait extends Actor {

        private EntitySprite sprite;
        private String clip;
        private Rectangle bounds;
        private float stateTime;

        void show(EntitySprite sprite, String clip) {
            this.sprite = sprite;
            this.clip = clip;
            this.bounds = sprite == null || clip == null ? null : sprite.bounds(clip);
            this.stateTime = 0f;
        }

        @Override
        public void act(float delta) {
            super.act(delta);
            stateTime += delta;
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            if (bounds == null || bounds.width <= 0f || bounds.height <= 0f) {
                return;
            }
            float scale = Math.min(getWidth() / bounds.width, getHeight() / bounds.height);
            com.badlogic.gdx.math.Matrix4 previous = batch.getTransformMatrix().cpy();
            batch.setTransformMatrix(new com.badlogic.gdx.math.Matrix4(previous)
                    .translate(getX() + getWidth() / 2f, getY() + getHeight() / 2f, 0f)
                    .scale(scale, scale, 1f));
            // PAM bounds are y-DOWN, so the art's centre sits at -(y + height/2) from the draw origin;
            // putting the origin at +(y + height/2) centres it. The same flip SpritePlacer.drawCentred
            // makes, and getting its sign wrong pushes the portrait a full icon out of its box.
            sprite.draw(batch, clip,
                    views.gdx.sprite.ClipMap.sample(sprite, clip, stateTime),
                    -(bounds.x + bounds.width / 2f), bounds.y + bounds.height / 2f, true, null);
            batch.setTransformMatrix(previous);
        }
    }
}
