package views.gdx.screens;

import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import views.gdx.ui.MenuStyles;

// The framed dialog every menu sits inside.
//
// image_ui_dialog_asset_dialogborder is the real game's dialog frame, and its _10 form is a TenPatch,
// so it stretches to whatever the screen puts in it without smearing the ornate corners. The inner
// background goes behind the contents so text is never read against the backdrop directly.
final class MenuPanel {

    private MenuPanel() {
    }

    static Table build(Skin skin, String title) {
        Table panel = new Table();
        Drawable border = MenuStyles.drawable(skin, MenuStyles.PANEL_BORDER);
        Drawable inner = MenuStyles.panelFill(skin);
        if (border != null) {
            // The ornate frame is translucent through its middle -- fine over the near-black vortex,
            // not fine over illustrated key art, where a zombie's face reads straight through the
            // button column and the backdrop's own baked-in logo ghosts up behind the title.
            //
            // So the opaque inner panel is drawn underneath it. Two drawables, one background, because
            // a Table has room for exactly one and every screen already calls this.
            panel.setBackground(inner == null ? border : layered(inner, border));
        }
        // The frame's art has a wide decorative margin; padding less than this puts the first widget
        // underneath it. Tighter top and bottom than sides, because the design space is 1280x720 and
        // vertical room is what the longer forms actually run out of.
        panel.pad(28f, 52f, 28f, 52f);

        if (title != null) {
            panel.add(MenuStyles.title(skin, title)).padBottom(14f).row();
        }
        return panel;
    }

    // How far the fill is pulled inside the frame's bounds.
    //
    // The frame is a rounded shape drawn inside a rectangular region, with transparent margin at the
    // corners. A fill drawn to the same rectangle therefore shows as square shoulders sticking out past
    // the gold edge. Insetting tucks it under the border, which is thick enough to cover the seam.
    private static final float FILL_INSET = 18f;

    // Draws one drawable over another as a single background. Sizing and padding come from the top
    // one, since that is the frame the layout was tuned against.
    private static Drawable layered(Drawable under, Drawable over) {
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
}
