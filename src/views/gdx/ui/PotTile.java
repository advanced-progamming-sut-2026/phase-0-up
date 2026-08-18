package views.gdx.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import models.greenhouse.PotState;
import views.gdx.sprite.EntitySprite;

// One greenhouse pot, standing on its own slat mat in the background painting.
//
// A Group with absolutely-placed children rather than a Table, because the pot's position is dictated by
// the artwork: GreenhouseScreen measures each mat in the background's own pixel space and puts a tile
// there. A layout container would want to distribute the twelve evenly, which is precisely what must not
// happen -- the mats sit in perspective, so their spacing is not uniform.
//
// Exactly ONE control is interactive per state, which is what keeps a 150px tile unambiguous:
//
//   LOCKED   padlock in the pot, "Buy" above it
//   EMPTY    bare soil, "Plant" above it
//   GROWING  the plant's idle animation, a timer banner over it, a gem button to hurry it
//   READY    the plant still animating in a gold pot -- the whole tile is the harvest button
//
// The tile knows nothing about greenhouses, commands, prices or profiles. It is handed a state and some
// strings and it draws them; what a press MEANS is the screen's business.
public final class PotTile extends Group {

    // Geometry, as fractions of the tile's own box, so the screen can size a tile from the background's
    // scale and have everything inside follow. The pot's aspect is the source art's (118x103).
    private static final float POT_ASPECT = 118f / 103f;
    private static final float POT_WIDTH_FRACTION = 0.68f;
    // Where the plant's feet land, as a fraction of the pot's height -- the visible soil inside the bowl,
    // not the tile's floor.
    //
    // The plant is drawn IN FRONT of the pot rather than behind it. Behind, with feet on the soil, the
    // pot's own front wall swallowed everything below its rim: two thirds of a Sunflower disappeared and
    // what was left read as a flower balanced on the rim. There is only one flat pot image, so a plant
    // cannot be sandwiched between a back and a front half -- and of the two options, a stem crossing the
    // rim is invisible while a decapitated plant is not.
    private static final float SOIL_FRACTION = 0.46f;
    private static final float PLANT_WIDTH_FRACTION = 0.58f;
    private static final float MARK_FRACTION = 0.24f;
    private static final float LOCK_WIDTH_FRACTION = 0.26f;
    private static final float LOCK_ASPECT = 42f / 55f;
    private static final float BANNER_WIDTH_FRACTION = 0.66f;
    private static final float BANNER_ASPECT = 100f / 36f;
    private static final float BUTTON_WIDTH_FRACTION = 0.64f;
    private static final float BUTTON_HEIGHT_FRACTION = 0.22f;
    private static final float GEM_WIDTH_FRACTION = 0.34f;
    private static final float GEM_HEIGHT_FRACTION = 0.20f;

    private static final Color TIMER_TEXT = new Color(1f, 0.96f, 0.82f, 1f);
    private static final Color GEM_TEXT = new Color(0.72f, 0.92f, 1f, 1f);
    // A pot still filling up is drawn a little dimmer than one that is done, so a full greenhouse still
    // reads at a glance.
    private static final Color GROWING_TINT = new Color(0.80f, 0.80f, 0.80f, 0.92f);

    // The pot's own 1-based coordinates -- what the greenhouse commands take. Held so the screen builds
    // "collect (3, 2)" from the tile itself rather than from a loop counter that may have drifted.
    private final int potX;
    private final int potY;

    private final Skin skin;
    private final Image pot;
    private final Image lock;
    private final Drawable potPlain;
    private final Drawable potRipe;

    // The "this one is finished" badge over a ripe pot.
    //
    // Not the Zen Garden's own HIGHLIGHT region, which the first pass used: it is entirely TRANSPARENT,
    // so the ready state had no marker at all and the bug was invisible in a screenshot. Its _2 variant
    // is four corner brackets (a selection reticle, wrong meaning) and READYTOWATER is a water droplet
    // (wrong meaning again -- there is no watering here). The shipped green tick says "done", which is
    // exactly the state, and it tells the player the pot is worth clicking.
    private static final String RIPE_MARK = "image_ui_generic_check_mark_sm";
    private final Image ripeMark;

    // Where the plant animates. Null-turf LiveEntityActor: it anchors the entity's FEET, which is what
    // puts a plant IN a pot rather than floating in the middle of a box.
    private final LiveEntityActor plant = new LiveEntityActor(null);

    private final Table banner;
    private final Label timer;
    private final Table gemButton;
    private final Label gemCost;
    private final TextButton action;

    // What is currently drawn, so a per-frame refresh only rebuilds when the pot actually changed --
    // otherwise the plant's animation restarts sixty times a second and stands still.
    private PotState shownState;
    private String shownPlant = "";

    public PotTile(Skin skin, int potX, int potY, float width, float height, PotArt art,
                   PotActions actions) {
        this.skin = skin;
        this.potX = potX;
        this.potY = potY;
        setSize(width, height);
        setTransform(true);

        potPlain = drawable(art.pot());
        potRipe = art.ripePot() == null ? potPlain : drawable(art.ripePot());

        Drawable tick = MenuStyles.drawable(skin, RIPE_MARK);
        ripeMark = tick == null ? new Image() : new Image(tick);
        pot = new Image(potPlain);
        lock = image(art.lock());
        banner = bannerBox(art.banner());
        timer = label("", TIMER_TEXT, 0.62f);
        banner.add(timer).grow();
        gemCost = label("", GEM_TEXT, 0.62f);
        gemButton = gemBox(art.gem(), actions);
        action = new TextButton("", skin.get(MenuStyles.BUTTON_GREEN_SMALL,
                TextButton.TextButtonStyle.class));

        placeChildren();
        wire(actions);
        show(PotState.LOCKED, null, "", 0, null);
    }

    // Back to front: the pot, then the plant standing in it, then everything that has to be read or
    // pressed.
    private void placeChildren() {
        float w = getWidth();
        float h = getHeight();
        float potW = w * POT_WIDTH_FRACTION;
        float potH = potW / POT_ASPECT;
        float soil = potH * SOIL_FRACTION;

        pot.setBounds((w - potW) / 2f, 0f, potW, potH);
        addActor(pot);

        float plantW = w * PLANT_WIDTH_FRACTION;
        plant.setBounds((w - plantW) / 2f, soil, plantW, h - soil);
        addActor(plant);

        float lockW = w * LOCK_WIDTH_FRACTION;
        float lockH = lockW / LOCK_ASPECT;
        lock.setBounds((w - lockW) / 2f, potH * 0.42f, lockW, lockH);
        addActor(lock);

        float buttonW = w * BUTTON_WIDTH_FRACTION;
        float buttonH = h * BUTTON_HEIGHT_FRACTION;
        action.setBounds((w - buttonW) / 2f, potH + buttonH * 0.25f, buttonW, buttonH);
        addActor(action);

        float bannerW = w * BANNER_WIDTH_FRACTION;
        float bannerH = bannerW / BANNER_ASPECT;
        // Above the plant, not over the pot: the timer is the one thing on a growing tile that has to be
        // legible at a glance, and the plant is the busiest thing on it.
        banner.setBounds((w - bannerW) / 2f, h - bannerH, bannerW, bannerH);
        addActor(banner);

        float gemW = w * GEM_WIDTH_FRACTION;
        float gemH = h * GEM_HEIGHT_FRACTION;
        gemButton.setBounds(w - gemW, potH * 0.72f, gemW, gemH);
        addActor(gemButton);

        // Top of the tile, where the timer banner sits on a growing pot -- the two states never overlap,
        // so they can share the space. The bob is what makes a ripe pot catch the eye across twelve of
        // them; it runs forever and costs nothing.
        float markSize = w * MARK_FRACTION;
        ripeMark.setBounds((w - markSize) / 2f, h - markSize, markSize, markSize);
        ripeMark.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.forever(
                com.badlogic.gdx.scenes.scene2d.actions.Actions.sequence(
                        com.badlogic.gdx.scenes.scene2d.actions.Actions.moveBy(0f, 6f, 0.7f,
                                com.badlogic.gdx.math.Interpolation.sine),
                        com.badlogic.gdx.scenes.scene2d.actions.Actions.moveBy(0f, -6f, 0.7f,
                                com.badlogic.gdx.math.Interpolation.sine))));
        addActor(ripeMark);
    }

    private void wire(PotActions actions) {
        action.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (shownState == PotState.LOCKED) {
                    actions.buy(potX, potY);
                } else if (shownState == PotState.EMPTY) {
                    actions.plant(potX, potY);
                }
            }
        });
        // Only a ripe pot listens on the tile itself. Every other state has its own button, and a
        // listener on the whole tile as well would make a press near a button ambiguous -- Scene2D
        // bubbles the event to the parent even after the button has handled it.
        addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (shownState == PotState.READY) {
                    actions.harvest(potX, potY);
                }
            }
        });
    }

    public int potX() {
        return potX;
    }

    public int potY() {
        return potY;
    }

    // Draws the pot as it now stands. Called every frame: a growing timer counts down live and a pot
    // ripens without anyone clicking anything, so nothing here may wait for an event.
    //
    // sprite may be null -- for an empty pot, and for a plant the asset dump has no animation for. Both
    // leave the pot empty rather than a hole in the grid.
    public void show(PotState state, String plantName, String timeLeft, int gems,
                     EntitySprite sprite) {
        String name = plantName == null ? "" : plantName;
        if (state != shownState || !name.equals(shownPlant)) {
            shownState = state;
            shownPlant = name;
            applyState(sprite);
        }
        timer.setText(timeLeft == null ? "" : timeLeft);
        gemCost.setText(String.valueOf(Math.max(0, gems)));
    }

    private void applyState(EntitySprite sprite) {
        boolean growing = shownState == PotState.GROWING;
        boolean ready = shownState == PotState.READY;
        boolean locked = shownState == PotState.LOCKED;

        pot.setDrawable(ready ? potRipe : potPlain);
        ripeMark.setVisible(ready);
        lock.setVisible(locked);
        banner.setVisible(growing);
        gemButton.setVisible(growing);

        action.setVisible(locked || shownState == PotState.EMPTY);
        action.setText(locked ? "Buy" : "Plant");
        action.setStyle(skin.get(locked ? MenuStyles.BUTTON_BROWN : MenuStyles.BUTTON_GREEN_SMALL,
                TextButton.TextButtonStyle.class));

        // A ripe tile is pressed anywhere, so it has to be hit-testable itself. Every other state is
        // pressed only through its own button, so the tile stays transparent to the pointer.
        setTouchable(ready ? Touchable.enabled : Touchable.childrenOnly);

        plant.setVisible(growing || ready);
        plant.show(growing || ready ? sprite : null);
        plant.setColor(growing ? GROWING_TINT : Color.WHITE);
    }

    // ---- small parts ----------------------------------------------------------------------------

    private Table bannerBox(TextureRegion art) {
        Table box = new Table();
        Drawable face = drawable(art);
        if (face != null) {
            box.setBackground(face);
        }
        return box;
    }

    // The gem speed-up control: the shipped gem, the price, and one hit area covering both. Not a
    // TextButton, which cannot hold an image beside its label -- the price has to be read WITH the gem
    // or it is just a number.
    private Table gemBox(TextureRegion gemArt, PotActions actions) {
        Table box = new Table();
        // Borrowed off the small green button's style rather than looked up as a drawable: "green_small"
        // names a TextButtonStyle, and skin.getDrawable would not find it.
        Drawable face = skin.get(MenuStyles.BUTTON_GREEN_SMALL, TextButton.TextButtonStyle.class).up;
        if (face != null) {
            box.setBackground(face);
        }
        box.pad(1f, 3f, 1f, 3f);
        Image gem = image(gemArt);
        box.add(gem).size(getHeight() * GEM_HEIGHT_FRACTION * 0.62f).padRight(2f);
        box.add(gemCost);
        box.setTouchable(Touchable.enabled);
        box.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                actions.hurry(potX, potY);
            }
        });
        return box;
    }

    private Label label(String text, Color colour, float scale) {
        Label label = MenuStyles.label(skin, text, MenuStyles.TEXT);
        label.setColor(colour);
        label.setFontScale(scale);
        return label;
    }

    // A missing region costs the tile that one layer and nothing else, so a renamed asset degrades the
    // look instead of taking the greenhouse down.
    private static Image image(TextureRegion region) {
        Drawable art = drawable(region);
        return art == null ? new Image() : new Image(art);
    }

    private static Drawable drawable(TextureRegion region) {
        return region == null ? null : new TextureRegionDrawable(region);
    }

    // The shipped Zen Garden regions a tile draws itself from. Grouped so the screen resolves them once
    // for all twelve tiles rather than each tile asking the atlas five times over.
    public record PotArt(TextureRegion pot, TextureRegion ripePot, TextureRegion lock,
                         TextureRegion banner, TextureRegion gem) {
    }

    // What a press means. Every coordinate is the 1-based pair the greenhouse commands take.
    public interface PotActions {
        void buy(int potX, int potY);

        void plant(int potX, int potY);

        void hurry(int potX, int potY);

        void harvest(int potX, int potY);
    }
}
