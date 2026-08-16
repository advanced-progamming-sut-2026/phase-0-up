package views.gdx.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import views.gdx.core.Assets;

// One tile in the almanac: a plant or a zombie, its name, and a line of state underneath.
//
// Shared by both tabs on purpose. A plant card says "Lv 2  3/5" and a zombie card says "Discovered" or
// nothing at all, but the frame, the sizing, the selection highlight and the way the art is fitted are
// the same job -- and a grid where the two tabs' tiles are subtly different sizes looks like a bug.
//
// The card knows nothing about plants, zombies, prices or profiles. It is handed an icon and two
// strings; what they mean is the screen's business.
public final class EntityCardActor extends Table {

    public static final float CARD_WIDTH = 122f;
    public static final float CARD_HEIGHT = 152f;

    // Fixed slots, all three of them.
    //
    // "Twin Sunflower" wraps to two lines and "Sunflower" does not, so a name cell sized to its content
    // makes those two cards different heights -- and a Table cell with a fixed size does not clip, so
    // the taller one simply overhangs its neighbours. Giving the name its own two-line slot puts every
    // footer on the same baseline and keeps the grid a grid.
    // Two lines' worth at NAME_FONT, measured off a screenshot rather than guessed: the line spacing
    // works out at ~23 units, so "Twin Sunflower" needs about 50 with its descenders. At 34 and again
    // at 46 its second line was drawn straight through the footer beneath it.
    private static final float ICON_HEIGHT = 66f;
    private static final float NAME_HEIGHT = 50f;
    private static final float NAME_FONT = 0.66f;
    private static final float FOOTER_HEIGHT = 16f;
    private static final float BORDER = 3f;

    private static final Color FACE = new Color(0f, 0f, 0f, 0.42f);
    private static final Color FACE_SELECTED = new Color(0.20f, 0.30f, 0.14f, 0.85f);
    private static final Color RIM = new Color(0.30f, 0.28f, 0.24f, 0.9f);
    private static final Color RIM_SELECTED = new Color(0.85f, 0.78f, 0.55f, 1f);
    private static final Color FOOTER = new Color(0.78f, 0.76f, 0.72f, 1f);

    private final Assets assets;
    private final Table face;
    private final Label caption;

    private boolean selected;

    // How big the padlock / question mark sits on the art.
    private static final float OVERLAY_SIZE = 26f;

    // overlay may be null. When present it is drawn ON the art -- a padlock for a plant not yet bought,
    // a question mark for a zombie not yet met -- instead of a word in the footer saying so.
    public EntityCardActor(Assets assets, Skin skin, Actor icon, Actor overlay, String name,
                           String footer, boolean dimmed) {
        this.assets = assets;

        face = new Table();
        face.pad(6f, 4f, 6f, 4f);

        // A fixed-height slot for the art rather than letting the icon size itself. Entities differ in
        // authored size by an order of magnitude, so cards laid out around their art would form a
        // ragged grid; EntityIcon scales to whatever box it is given.
        //
        // Stacked, so a padlock or a question mark can sit ON the art instead of being spelled out
        // underneath it. A word in the footer said the same thing as the dimmed art did, twice.
        com.badlogic.gdx.scenes.scene2d.ui.Stack art = new com.badlogic.gdx.scenes.scene2d.ui.Stack();
        art.add(icon);
        if (overlay != null) {
            // Bottom-right, not centred. A 40px padlock over a 106x66 tile covers the plant entirely,
            // which defeats the point: the player should still recognise WHAT is locked.
            com.badlogic.gdx.scenes.scene2d.ui.Table corner =
                    new com.badlogic.gdx.scenes.scene2d.ui.Table();
            corner.add(overlay).size(OVERLAY_SIZE).expand().bottom().right().pad(2f);
            art.add(corner);
        }
        face.add(art).size(CARD_WIDTH - 16f, ICON_HEIGHT).row();

        Label title = MenuStyles.label(skin, name, MenuStyles.TEXT);
        title.setWrap(true);
        // Top, not centre: a one-line name and a two-line name then start at the same height, so the
        // row of cards reads along one baseline instead of each name floating in its own slot.
        title.setAlignment(Align.top);
        title.setFontScale(NAME_FONT);
        if (dimmed) {
            title.setColor(FOOTER);
        }
        face.add(title).growX().height(NAME_HEIGHT).padTop(2f).row();

        caption = MenuStyles.label(skin, footer == null ? "" : footer, MenuStyles.TEXT);
        caption.setAlignment(Align.center);
        caption.setFontScale(0.66f);
        caption.setColor(FOOTER);
        face.add(caption).growX().height(FOOTER_HEIGHT).row();

        pad(BORDER);
        add(face).grow();
        applySelection();
    }

    public void setFooter(String text) {
        caption.setText(text == null ? "" : text);
    }

    public void setSelected(boolean selected) {
        if (this.selected == selected) {
            return;
        }
        this.selected = selected;
        applySelection();
    }

    // The rim is the OUTER table's background and the fill is the inner one's, so the border is drawn
    // as padding rather than as a nine-patch the dump does not have.
    private void applySelection() {
        setBackground(assets.solid(selected ? RIM_SELECTED : RIM));
        face.setBackground(assets.solid(selected ? FACE_SELECTED : FACE));
    }
}
