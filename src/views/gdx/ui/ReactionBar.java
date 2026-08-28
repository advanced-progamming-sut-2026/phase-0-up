package views.gdx.ui;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import models.social.Reaction;
import models.social.ReactionKind;
import views.gdx.core.Assets;
import views.gdx.sprite.SpriteRegistry;

import java.util.function.Consumer;

// The nine things you can say to your opponent, in the corner of the lawn.
//
// Collapsed to a single button until it is opened, which is the whole design constraint: this sits on
// top of a board somebody is playing, and a permanent 3x3 grid of pictures would cover three columns
// of lawn. Bottom RIGHT, because the seed bank and the toolbar own the top left and the wave meter
// owns the bottom middle -- the one corner of this HUD that is empty.
//
// Touchable.childrenOnly on the outer table, so the empty space around the bar does not swallow
// clicks meant for the lawn behind it. Getting this wrong makes the bottom-right of the board
// mysteriously unclickable, which is exactly the kind of bug nobody attributes to a chat widget.
public final class ReactionBar {

    // A solid fill rather than UiArt.PANEL.
    //
    // That art is the HUD's 3-slice strip: authored wide and short and mostly translucent, and
    // stretched to a panel three rows tall it washes out to the point where the lawn reads straight
    // through the captions -- which was exactly what the first version looked like, with five
    // sun-maker zombies visible through the words. Same trade ZombieSpawnerWindow made, for the same
    // reason: this panel sits over the busiest corner of the board by definition.
    private static final com.badlogic.gdx.graphics.Color PANEL =
            new com.badlogic.gdx.graphics.Color(0.11f, 0.13f, 0.17f, 0.94f);
    private static final com.badlogic.gdx.graphics.Color CELL =
            new com.badlogic.gdx.graphics.Color(0.20f, 0.24f, 0.29f, 1f);

    private static final float ICON = 46f;
    private static final float CELL_WIDTH = 132f;
    private static final float CELL_HEIGHT = 74f;

    private final Assets assets;
    private final SpriteRegistry sprites;
    private final Consumer<Reaction> onSend;

    private final Table root = new Table();
    private final Table grid = new Table();
    private final TextButton toggle;

    private boolean open;

    public ReactionBar(Assets assets, SpriteRegistry sprites, Stage stage,
                       Consumer<Reaction> onSend) {
        this.assets = assets;
        this.sprites = sprites;
        this.onSend = onSend;

        toggle = MenuStyles.button(assets.skin(), "Say...", MenuStyles.BUTTON_BROWN);
        toggle.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                setOpen(!open);
            }
        });

        buildGrid();
        grid.setVisible(false);

        root.setFillParent(true);
        root.setTouchable(Touchable.childrenOnly);
        root.bottom().right().pad(12f);
        Table column = new Table();
        column.add(grid).right().row();
        column.add(toggle).width(140f).height(44f).padTop(6f).right();
        root.add(column);
        stage.addActor(root);
    }

    // One row per kind, three to a row: the same shape the spec lists them in, and the same shape the
    // wire carries (kind + index 0..2).
    private void buildGrid() {
        Table panel = new Table();
        panel.setBackground(assets.solid(PANEL));
        panel.pad(8f);
        for (ReactionKind kind : ReactionKind.values()) {
            for (Reaction reaction : Reaction.of(kind)) {
                panel.add(cell(reaction)).size(CELL_WIDTH, CELL_HEIGHT).pad(3f);
            }
            panel.row();
        }
        grid.add(panel);
    }

    private Table cell(Reaction reaction) {
        Table cell = new Table();
        cell.setBackground(assets.solid(CELL));
        cell.setTouchable(Touchable.enabled);

        Actor art = artFor(reaction);
        if (art != null) {
            cell.add(art).size(ICON).row();
        }
        Label caption = new Label(ReactionArt.caption(reaction), assets.skin());
        caption.setAlignment(Align.center);
        caption.setWrap(true);
        caption.setFontScale(0.75f);
        cell.add(caption).width(CELL_WIDTH - 10f).expandX();

        cell.addListener(new ClickListener() {
            @Override
            public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                onSend.accept(reaction);
                // Closed after one. A grid that stays open covers the corner of the lawn, and sending
                // is a one-shot gesture -- the rate limit on the server would refuse a second anyway.
                setOpen(false);
            }
        });
        return cell;
    }

    // A picture for the two picture kinds, nothing for a text one -- its caption IS the reaction, and
    // drawing an icon above it would be decoration standing in for content.
    private Actor artFor(Reaction reaction) {
        String region = ReactionArt.region(reaction);
        if (region != null) {
            try {
                Image image = new Image(new TextureRegionDrawable(assets.region(region)));
                image.setScaling(com.badlogic.gdx.utils.Scaling.fit);
                return image;
            } catch (RuntimeException missing) {
                return null;   // the caption still says what it is
            }
        }
        String animation = ReactionArt.animation(reaction);
        if (animation == null) {
            return null;
        }
        EntityIcon icon = new EntityIcon(sprites.get(animation));
        return icon.hasArt() ? icon : null;
    }

    // For -Dpvz.reactionCheck, so a screenshot catches the grid open rather than the one button that
    // hides it.
    public void open() {
        setOpen(true);
    }

    private void setOpen(boolean open) {
        this.open = open;
        grid.setVisible(open);
        toggle.setText(open ? "Never Mind" : "Say...");
    }

    public void dispose() {
        root.remove();
    }
}
