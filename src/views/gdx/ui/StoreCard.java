package views.gdx.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.Container;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Scaling;

// One product, as a card.
//
// The art is the real game's own promoted-offer card: a rounded brown frame around a teal starburst,
// with a gold banner across the top that a title is clearly meant to sit on. It ships at 343x498, and
// every card here is a different shape from that, so it is drawn as a nine-patch -- the corners, the
// rim and the gold banner keep their own pixels and only the starburst in the middle stretches, which
// it can do invisibly because it is a soft gradient.
//
// ## Why the slots are fixed
//
// Six cards sit in one grid and they carry different amounts of furniture: the daily deal has a
// countdown and a struck-out price, the chosen-packet card needs a plant picker, the pot has neither.
// Built by appending rows in call order, each card would end up a different height and the grid would
// read as six accidents.
//
// So every slot exists from the moment the card does, in a fixed order, and the setters only fill them.
// An unfilled slot measures zero. The ICON is the one cell that grows, which is what absorbs the
// difference: a card carrying a picker draws a smaller product image rather than a taller card.
public final class StoreCard extends Table {

    // The promoted-offer card from the store atlas. Confirmed with -Dpvz.probeRegions.
    public static final String CARD_ART = "IMAGE_UI_STORE_CARD_PROMOTED_BACKGROUND";

    // Enough of the source to keep the gold banner, the rounded corners and the bottom rim intact.
    // The banner is about 47 of the 498 rows and the rim another 8, so a border fraction below ~0.11
    // would start stretching the banner itself.
    private static final float CARD_BORDER = 0.13f;

    // The title sits ON the gold banner, so its cell is as tall as the banner rather than as tall as
    // the text.
    private static final float TITLE_HEIGHT = 42f;
    private static final float ACTION_HEIGHT = 40f;
    // Small enough that the longest title in the shop -- "Greenhouse Pot" -- stays on one line inside a
    // 272-wide card. A wrapped title is not merely ugly here: the banner is one line tall, so a second
    // line is drawn off the top of the card and over whatever is above it in the grid.
    private static final float TITLE_SCALE = 0.58f;
    private static final float NOTE_SCALE = 0.66f;

    // Dark brown, the colour of the banner's own lettering in the source art. White on that gold is
    // exactly as hard to read as it sounds.
    private static final Color TITLE_TEXT = new Color(0.28f, 0.16f, 0.04f, 1f);
    private static final Color NOTE_TEXT = new Color(1f, 0.93f, 0.72f, 1f);

    // How far a promoted card breathes, and how slowly. Small and slow on purpose: this is meant to
    // catch the eye on the way past, not to pull it off whatever the player is reading.
    private static final float PROMOTE_SCALE = 1.02f;
    private static final float PROMOTE_SECONDS = 1.3f;

    private final Label titleLabel;
    private final Image product = new Image();
    private final Label noteLabel;
    private final Cell<Label> noteCell;
    private final Container<Actor> controlSlot = new Container<>();
    private final Table priceRow = new Table();
    private final Container<Actor> actionSlot = new Container<>();

    public StoreCard(Skin skin, UiArt art, float width, float height) {
        Drawable background = art.stretchable(CARD_ART, CARD_BORDER);
        if (background != null) {
            setBackground(background);
        }
        setSize(width, height);

        titleLabel = MenuStyles.label(skin, "", MenuStyles.HEADING);
        titleLabel.setFontScale(TITLE_SCALE);
        titleLabel.setColor(TITLE_TEXT);
        titleLabel.setAlignment(Align.center);

        noteLabel = MenuStyles.label(skin, "", MenuStyles.TEXT);
        noteLabel.setFontScale(NOTE_SCALE);
        noteLabel.setColor(NOTE_TEXT);
        noteLabel.setAlignment(Align.center);

        // Scaling.fit, always. The product images are shipped at wildly different sizes -- a 192x192
        // plant-food jar next to a 120x78 seed packet -- and left to itself an Image stretches its
        // drawable to the cell, which turns the packet into a smear.
        product.setScaling(Scaling.fit);

        pad(8f, 12f, 12f, 12f);
        // The title sits directly on the card art's own gold banner. An earmark ribbon was tried behind
        // it first and taken out again: the banner IS the ribbon, and a second one over it read as a
        // sticker somebody had left on the card.
        add(titleLabel).growX().height(TITLE_HEIGHT).row();
        add(product).grow().pad(2f, 6f, 2f, 6f).row();
        // Collapsed until something is written into it. An empty Label is not a zero-height Label -- it
        // still reports its font's line height -- and on the one card that carries a picker instead of a
        // description those twenty pixels come straight out of the product image.
        noteCell = add(noteLabel).growX().height(0f);
        row();
        add(controlSlot).padTop(2f).row();
        // growX, or the spacer inside the row has nothing to spread: the price and the stepper measure
        // to their own contents, and a row sized to those two puts the stepper's left arrow on top of
        // the price it is meant to stand clear of.
        add(priceRow).growX().padTop(4f).row();
        add(actionSlot).growX().height(ACTION_HEIGHT).padTop(6f);
    }

    // Deliberately NOT wrapped. The banner is one line tall, so a title too long to fit does not get a
    // second line -- it gets drawn off the top of the card and across whatever is above it in the grid.
    // Ellipsis is the honest failure here, and the titles are short enough that it never fires.
    public StoreCard title(String text) {
        titleLabel.setText(text);
        titleLabel.setEllipsis(true);
        return this;
    }

    public StoreCard icon(TextureRegion region) {
        product.setDrawable(region == null ? null : new TextureRegionDrawable(region));
        return this;
    }

    // The line under the product: the deal's countdown, or why a card cannot be bought. Returned so the
    // screen can keep writing to it -- the timer is re-read every frame.
    public Label note(String text) {
        boolean blank = text == null || text.isBlank();
        noteLabel.setText(blank ? "" : text);
        noteCell.height(blank ? 0f : noteLabel.getPrefHeight());
        invalidateHierarchy();
        return noteLabel;
    }

    public StoreCard control(Actor actor) {
        controlSlot.setActor(actor);
        return this;
    }

    // Price on the left, quantity stepper on the right, sharing one row. They were separate rows first,
    // which cost thirty pixels the chosen-packet card did not have to spare -- it is the one carrying
    // both a plant picker and a stepper, and the product image had shrunk to a smudge.
    public StoreCard price(Actor priceTag, Actor quantity) {
        priceRow.clearChildren();
        priceRow.add(priceTag).left();
        if (quantity != null) {
            priceRow.add().expandX();
            priceRow.add(quantity).right();
        }
        return this;
    }

    public StoreCard action(Actor actor) {
        actionSlot.setActor(actor);
        actionSlot.fill();
        return this;
    }

    // A slow swell, for the card that is on offer.
    //
    // Scale needs a transform and a centred origin, and the origin has to be re-taken here rather than
    // in the constructor: an actor has no size until its first layout pass, so an origin set at build
    // time is the bottom-left corner and the card would grow out of it sideways.
    public StoreCard promote() {
        setTransform(true);
        setOrigin(Align.center);
        addAction(Actions.forever(Actions.sequence(
                Actions.scaleTo(PROMOTE_SCALE, PROMOTE_SCALE, PROMOTE_SECONDS, Interpolation.sine),
                Actions.scaleTo(1f, 1f, PROMOTE_SECONDS, Interpolation.sine))));
        return this;
    }

    // Fades the whole card, for stock that is gone for the day. Faded rather than hidden, so the offer
    // is still readable -- "you already bought this" and "this does not exist" are different things and
    // should not look the same.
    //
    // Alpha, not a grey tint. A Group hands its children only its ALPHA, never its rgb, so setting the
    // card grey dims the frame and leaves the title, the price and the product image at full strength --
    // which looks like a rendering fault rather than like a state.
    public StoreCard exhaust() {
        getColor().a = 0.55f;
        return this;
    }
}
