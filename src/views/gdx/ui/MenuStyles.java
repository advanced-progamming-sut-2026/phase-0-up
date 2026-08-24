package views.gdx.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Align;

// The menu chrome the skin actually ships, named once.
//
// pvz2_skin.json carries the real game's UI: TenPatch-stretchable brown/green/purple buttons, the
// mainmenu name field, House of Terror for button text and the almanac face for body text. None of it
// had been used yet -- the in-game HUD is drawn straight from the atlas -- so the style names are
// recorded here rather than spelled out as string literals across nine screens, where a typo becomes a
// GdxRuntimeException at screen-construction time and nowhere earlier.
//
// Everything below was read out of the skin, not guessed. If a name disappears in a skin update, the
// helpers fall back rather than taking the screen down with them.
public final class MenuStyles {

    // TextButton styles. All four are TenPatch, so they stretch to their label without distorting the
    // ornate corners.
    public static final String BUTTON_BROWN = "brown";
    public static final String BUTTON_GREEN = "green";
    public static final String BUTTON_PURPLE = "purple";
    public static final String BUTTON_GREEN_SMALL = "green_small";

    // Label styles. "big" and "medium" are the PvZ display face; the outline variants are what the
    // real game uses over busy artwork, which is exactly the situation on every one of these screens.
    public static final String TEXT = "default";
    public static final String TITLE = "big_outline";
    public static final String HEADING = "medium_outline";
    public static final String BODY_BIG = "medium";

    // Panels, from the dialog atlas.
    public static final String PANEL_BORDER = "image_ui_dialog_asset_dialogborder_10";
    public static final String PANEL_INNER = "image_ui_dialog_asset_inner_bkgd_10";
    public static final String PANEL_TEXTURE = "image_ui_dialog_asset_dialogtexture";
    public static final String DIVIDER = "image_ui_generic_4pxdivider";

    // The full-screen scrim behind a menu. Defined in the skin as a tinted white pixel at ~39% black,
    // which is what the real game darkens the world with when a dialog opens.
    public static final String MODAL_SCRIM = "modal_background";

    private MenuStyles() {
    }

    // A style-safe Label. A missing style is a skin problem, not a reason for the screen to fail to
    // open, so it degrades to the default face.
    public static Label label(Skin skin, String text, String style) {
        Label label = skin.has(style, Label.LabelStyle.class)
                ? new Label(text, skin, style)
                : new Label(text, skin);
        label.setAlignment(Align.center);
        return label;
    }

    public static Label title(Skin skin, String text) {
        return label(skin, text, TITLE);
    }

    // Every menu button in the game comes from here, which is why the hover and press feedback is
    // attached here too rather than by each screen: one place to change, and no screen can forget.
    public static TextButton button(Skin skin, String text, String style) {
        TextButton button = skin.has(style, TextButton.TextButtonStyle.class)
                ? new TextButton(text, skin, style)
                : new TextButton(text, skin);
        return ButtonJuice.applyTo(button);
    }

    public static TextField field(Skin skin, String placeholder) {
        TextField field = new TextField("", skin);
        field.setMessageText(placeholder);
        field.setAlignment(Align.center);
        return field;
    }

    // A password field. LibGDX will not mask without both a mask character and the flag.
    public static TextField secret(Skin skin, String placeholder) {
        TextField field = field(skin, placeholder);
        field.setPasswordCharacter('*');
        field.setPasswordMode(true);
        return field;
    }

    // Asks for the drawable rather than testing for one first.
    //
    // skin.has(name, Drawable.class) looks only at what is already REGISTERED as a Drawable -- the
    // TenPatch panels and tints the JSON declares. A plain atlas region is registered as a
    // TextureRegion and only becomes a Drawable when getDrawable() wraps it, so the test answered
    // "no" for every ordinary sprite in the atlas. That silently cost the checkbox its tick and the
    // cyclers their green arrows, both of which fell back to text and looked merely plain rather than
    // broken.
    // What goes behind a dialog's contents, under the ornate frame.
    //
    // Deliberately NOT the shipped inner panel (image_ui_dialog_asset_inner_bkgd), which is cream --
    // correct for the real game, where dialog text is dark brown, and wrong here, where every label in
    // the project is white. Dark keeps the existing text readable while still hiding the illustrated
    // backdrop, which is the point: the frame's own middle is translucent.
    public static Drawable panelFill(Skin skin) {
        try {
            // Near-opaque, not merely dark. At 0.88 the backdrop's own baked-in logo still read
            // straight through the title -- illustrated art is bright enough that a tenth of it is
            // plenty to see.
            return skin.newDrawable("white-pixel", new Color(0.11f, 0.09f, 0.07f, 0.97f));
        } catch (RuntimeException missing) {
            return null;
        }
    }

    // Draws one drawable over another as a single background: an opaque fill tucked under the ornate
    // frame, because the frame's own middle is translucent and anything lit behind it reads through the
    // text.
    //
    // `inset` is how far the fill is pulled inside the frame's bounds. The frame is a rounded shape in a
    // rectangular region, so a fill drawn to the same rectangle shows as square shoulders sticking out
    // past the gold edge; insetting tucks it under the border, which is thick enough to cover the seam.
    //
    // MenuPanel and GameOverlays each grew their own private copy of this before it was worth naming,
    // and each has its inset tuned to a panel that already looks right -- so they are left alone rather
    // than migrated for tidiness. New panels use this one.
    public static Drawable layered(Drawable under, Drawable over, float inset) {
        if (under == null) {
            return over;
        }
        return new com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable(over) {
            @Override
            public void draw(com.badlogic.gdx.graphics.g2d.Batch batch,
                             float x, float y, float width, float height) {
                under.draw(batch, x + inset, y + inset, width - inset * 2f, height - inset * 2f);
                over.draw(batch, x, y, width, height);
            }
        };
    }

    // The same drawable, reporting more horizontal padding.
    //
    // Widgets that inset their contents -- TextField's text, a Table's cells -- read the padding off
    // their background rather than from a setting of their own. Mutating the skin's drawable would move
    // every other user of it, so this delegates instead.
    public static Drawable insetBy(Drawable base, float extra) {
        com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable padded =
                new com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable(base) {
                    @Override
                    public void draw(com.badlogic.gdx.graphics.g2d.Batch batch,
                                     float x, float y, float width, float height) {
                        base.draw(batch, x, y, width, height);
                    }
                };
        padded.setLeftWidth(base.getLeftWidth() + extra);
        padded.setRightWidth(base.getRightWidth() + extra);
        return padded;
    }

    public static Drawable drawable(Skin skin, String name) {
        try {
            return skin.getDrawable(name);
        } catch (RuntimeException missing) {
            return null;
        }
    }

    // An on/off control, as a button that changes colour.
    //
    // Not a CheckBox. The atlas does ship image_ui_mainmenu_checkbox_enabled/_disabled, but those are
    // the ENABLED and DISABLED states of one ticked box -- a bright tick and a greyed tick -- not
    // ticked and unticked. Wiring them to on and off puts a tick on screen next to a setting that is
    // switched off, which is worse than plain. There is no empty-box art in the dump.
    //
    // Green for on and brown for off uses the same buttons as everything else on these screens, and
    // the state is legible without a legend.
    public static TextButton toggle(Skin skin, String text, boolean initial,
                                    java.util.function.Consumer<Boolean> onChange) {
        TextButton button = button(skin, label(text, initial), BUTTON_GREEN);
        applyToggleStyle(skin, button, initial);
        button.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ChangeListener() {
            private boolean on = initial;

            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                on = !on;
                applyToggleStyle(skin, button, on);
                button.setText(label(text, on));
                if (onChange != null) {
                    onChange.accept(on);
                }
            }
        });
        return button;
    }

    // Green when on, GREY when off -- not brown.
    //
    // Brown is what Back and every other action button uses, so an off toggle was indistinguishable
    // from something you press to go somewhere. The grey is the skin's own disabled-button art, which
    // is exactly the "this is a state, and it is currently off" look, and it ships.
    private static final String BUTTON_OFF_ART = "image_ui_generic_disabledbutton_10";

    private static void applyToggleStyle(Skin skin, TextButton button, boolean on) {
        if (on) {
            button.setStyle(skin.get(BUTTON_GREEN, TextButton.TextButtonStyle.class));
            return;
        }
        TextButton.TextButtonStyle off = new TextButton.TextButtonStyle(
                skin.get(BUTTON_BROWN, TextButton.TextButtonStyle.class));
        Drawable grey = drawable(skin, BUTTON_OFF_ART);
        if (grey != null) {
            off.up = grey;
            off.down = grey;
        }
        off.fontColor = new Color(0.72f, 0.70f, 0.66f, 1f);
        button.setStyle(off);
    }

    // A blank caption means the switch stands beside its own label already, as in the settings form --
    // so it says just "On", not ": On".
    private static String label(String text, boolean on) {
        String state = on ? "On" : "Off";
        return text == null || text.isBlank() ? state : text + ": " + state;
    }

    // Arrows, for the Cycler. Green because that is the only complete left/right pair in the atlas.
    public static final String ARROW_LEFT = "image_ui_generic_arrow_left_green";
    public static final String ARROW_RIGHT = "image_ui_generic_arrow_right_green";

    // Red, for the unread-news badge and anything else that has to shout.
    public static final Color BADGE_RED = new Color(0.85f, 0.13f, 0.13f, 1f);
}
