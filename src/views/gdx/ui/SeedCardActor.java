package views.gdx.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Scaling;
import models.game.SeedPacket;
import views.gdx.core.Assets;

// One seed packet: what it plants, what it costs, whether it is boosted, and whether it can be used
// right now.
//
// The SAME actor in all three places a packet appears -- the in-game bank, the seed-selection bar and
// the available grid on that screen. That is the point of it: a plant that looks one way while you pick
// it and another way once you are playing reads as two different plants, and three screens each drawing
// their own version is three places for the sun cost to go stale.
//
// The card renders four states and decides none of them itself -- readiness comes from the packet's own
// cooldown, affordability from the wallet, the boost from the packet. Nothing here duplicates a rule;
// if the card says a plant is unavailable, the command would refuse it for exactly the same reason.
public final class SeedCardActor extends Table {

    public static final float CARD_WIDTH = 74f;
    public static final float CARD_HEIGHT = 92f;

    // Tints over the shipped seed-packet art rather than flat fills standing in for it. WHITE leaves
    // the packet its own colour; the selected tint just brightens it.
    private static final Color PANEL = Color.WHITE;
    private static final Color PANEL_SELECTED = new Color(1f, 1f, 0.62f, 1f);
    // Drawn over the card from the top down as the packet recharges -- the same wipe the original uses.
    // A radial sweep needs a mesh; a wipe needs one quad and reads identically at this size.
    private static final Color COOLDOWN_VEIL = new Color(0.05f, 0.07f, 0.12f, 0.72f);
    // A flat dim for "you cannot afford this", so it is distinguishable at a glance from recharging.
    private static final Color BROKE_VEIL = new Color(0.05f, 0.05f, 0.08f, 0.45f);
    // An empty bar slot: the same frame, sunk back far enough to read as a hole rather than as a card.
    private static final Color EMPTY_SLOT = new Color(0.30f, 0.32f, 0.30f, 0.75f);
    // Red-ish, for a price the player cannot currently meet.
    private static final Color BROKE_TEXT = new Color(1f, 0.55f, 0.5f, 1f);
    // The shimmer that crosses a boosted packet.
    private static final Color BOOST_SHEEN = new Color(1f, 0.96f, 0.72f, 1f);

    private static final float BOOST_SHEEN_ALPHA = 0.16f;
    private static final float BOOST_SHEEN_HZ = 0.55f;

    // The sun coin beside the price. Sized to the cost row rather than to the art, which ships at
    // 70x71 and would otherwise be the tallest thing on the card.
    private static final float SUN_SIZE = 18f;
    private static final float COST_ROW_HEIGHT = 22f;

    // How far inside the frame the portrait and the price sit.
    //
    // Set explicitly rather than inherited from the background. A Table with a nine-patch background
    // and no pad of its own adopts the patch's borders as padding, and this patch's borders are a
    // FRACTION of a 119x75 source -- 26 units a side at the old 0.22 -- which on a 74-wide card left
    // 22 units for the plant. That is why a card in the bank and a card on the selection screen were
    // visibly different objects: the selection screen never sized its cells, so its cards grew to
    // whatever that padding demanded while the bank's were squeezed into 74.
    private static final float PAD_TOP = 5f;
    private static final float PAD_SIDE = 7f;
    private static final float PAD_BOTTOM = 4f;
    // Enough to keep the frame's rounded corners intact, no more. See the padding note above.
    private static final float FRAME_BORDER = 0.12f;

    private final Assets assets;
    private final SeedPacket packet;
    private final int cost;
    private final Label costLabel;

    private final Drawable packetFrame;
    private final Drawable boostFrame;

    private Table costRow;
    private Image sunCoin;
    private int supply = -1;   // -1 = priced in sun; >= 0 = a count in hand, see setSupply

    private boolean selected;
    private boolean affordable = true;
    private boolean boosted;
    private float cooldownFraction;   // 1 = just used, 0 = ready
    private float sheenClock;

    // Re-tinted in place rather than rebuilt: this is set on every frame a boosted card is on screen.
    private final Color sheenTint = new Color(BOOST_SHEEN);

    // `icon` is an EntityIcon rather than a PlantIcon because I, Zombie's roster puts ZOMBIES on these
    // cards. PlantIcon is a subclass that adds nothing but a readable name at the plant call sites, so
    // widening the parameter changes none of them -- and a zombie needs its armour visibility map, which
    // only EntityIcon.showing can carry.
    public SeedCardActor(Assets assets, UiArt art, SeedPacket packet, int cost, EntityIcon icon) {
        this.assets = assets;
        this.packet = packet;
        this.cost = cost;

        // The card is composited from two shipped pieces, because the plant images are PORTRAITS on
        // transparent backgrounds -- drawn alone they float on the lawn with no card around them. The
        // blank packet from the same atlas supplies the frame and the portrait sits on it, which is how
        // the real card is put together.
        //
        // Nine-sliced rather than stretched: the frame is authored wider than it is tall (119x75) while
        // the bank wants taller cards, and stretching it outright would smear its border art. Slicing
        // keeps the corners intact and stretches only the middle.
        this.packetFrame = art.stretchable(UiArt.SEED_PACKET, FRAME_BORDER);
        // Its boosted twin, shipped at exactly the same size, so this is a background swap and not a
        // relayout. See setBoosted.
        this.boostFrame = art.stretchable(UiArt.SEED_PACKET_BOOST, FRAME_BORDER);
        setBackground(packetFrame);
        pad(PAD_TOP, PAD_SIDE, PAD_BOTTOM, PAD_SIDE);
        setColor(PANEL);
        setSize(CARD_WIDTH, CARD_HEIGHT);

        addPortrait(art, icon);

        this.costLabel = new Label(String.valueOf(cost), assets.skin());
        costLabel.setAlignment(Align.center);
        add(costRow(art)).height(COST_ROW_HEIGHT).padBottom(2f).center();

        setBoosted(packet.isBoosted());
    }

    // The plant fills whatever room the price leaves, rather than claiming a fixed box.
    //
    // That is what lets one card class be drawn at two sizes -- 74x92 in the bank, larger on the
    // selection screen where there is room for it -- without the art detaching from its frame. Both
    // branches fit rather than stretch, so a portrait keeps its aspect at either size.
    private void addPortrait(UiArt art, EntityIcon icon) {
        TextureRegion portrait = art.packet(packet.getPlantType());
        if (portrait != null) {
            Image image = new Image(new TextureRegionDrawable(portrait));
            image.setScaling(Scaling.fit);
            add(image).grow().row();
        } else {
            // No shipped portrait for this plant: fall back to its live animation.
            add(icon).grow().row();
        }
    }

    // The size a card takes when a cell does not say otherwise. Without this the card's preferred size
    // is whatever its contents happen to want, which is how the same actor ended up noticeably larger
    // on the selection screen than in the bank.
    @Override
    public float getPrefWidth() {
        return CARD_WIDTH;
    }

    @Override
    public float getPrefHeight() {
        return CARD_HEIGHT;
    }

    // Sun coin, then the number.
    //
    // A bare "150" on a card is a quantity of nothing in particular -- it reads as a level, a count or a
    // price depending on who is looking. The coin is the same one the HUD counts sun with, so the card
    // and the sun meter name the same currency in the same picture.
    private Table costRow(UiArt art) {
        TextureRegion sun = art.region(UiArt.SUN);
        if (sun != null) {
            sunCoin = new Image(new TextureRegionDrawable(sun));
            sunCoin.setScaling(Scaling.fit);
        }
        costRow = new Table();
        fillCostRow();
        return costRow;
    }

    private void fillCostRow() {
        costRow.clearChildren();
        if (supply < 0 && sunCoin != null) {
            costRow.add(sunCoin).size(SUN_SIZE).padRight(3f);
        }
        costRow.add(costLabel);
    }

    // What the card says under the portrait, for a mode that hands plants out instead of selling them.
    //
    //   -1  priced in sun -- every ordinary level, and the default
    //    0  nothing at all -- one card IS one item, so a count would be noise (the bowling conveyor)
    //   >0  "xN" -- a hand that stacks (Vasebreaker's collected packets)
    //
    // The coin is REMOVED rather than hidden, because a hidden actor still holds its cell and would
    // leave the count typeset off-centre under a gap where a price used to be. A packet that came out
    // of a vase has no price, and showing "0 sun" for it would be a different lie from the one this is
    // fixing.
    public void setSupply(int count) {
        if (supply == count) {
            return;
        }
        supply = count;
        costLabel.setText(caption(count));
        fillCostRow();
    }

    private String caption(int count) {
        if (count < 0) {
            return String.valueOf(cost);
        }
        return count == 0 ? "" : "x" + count;
    }

    public String plantType() {
        return packet.getPlantType();
    }

    public int sunCost() {
        return cost;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
        // Tinting the frame, not swapping it: the packet art stays, it just lights up.
        setColor(selected ? PANEL_SELECTED : PANEL);
    }

    public boolean isSelected() {
        return selected;
    }

    // Boosted packets wear the game's own boosted frame: a gold sunburst that ships beside the plain
    // green one at exactly the same size, so this swaps a background and moves nothing.
    //
    // The shimmer over it is DRAWN rather than animated with an Action, because ButtonJuice calls
    // clearActions() on every hover -- a forever-action here would be silently cancelled the first time
    // the pointer crossed the card, which is the one moment anybody is looking at it.
    public void setBoosted(boolean value) {
        this.boosted = value;
        setBackground(value && boostFrame != null ? boostFrame : packetFrame);
    }

    public boolean isBoosted() {
        return boosted;
    }

    // Refreshed every frame from live state. Cheap enough to do unconditionally, and doing it
    // unconditionally is what stops the card lying after a cheat or an upgrade changes the numbers.
    public void refresh(long currentTick, int sunAvailable) {
        this.affordable = sunAvailable >= cost;
        setBoosted(packet.isBoosted());

        double remaining = packet.getRemainingCooldownSeconds(currentTick);
        double total = Math.max(1, packet.getCooldownDuration());
        this.cooldownFraction = (float) Math.max(0d, Math.min(1d, remaining / total));

        costLabel.setColor(affordable ? Color.WHITE : BROKE_TEXT);
    }

    public boolean isUsable() {
        return affordable && cooldownFraction <= 0f;
    }

    // The skin's own padlock, over a seed a mode has bolted to the bar (Locked Plants' forced loadout)
    // and over a slot it has welded shut. Missing art costs the badge, not the card.
    public static final String LOCK_ART = "image_ui_cards_lock_medium";
    private static final float BADGE_FRACTION = 0.36f;
    private static final float BADGE_PAD = 3f;

    private Drawable lockBadge;

    // A padlock in the corner, for a seed the player is not allowed to take off the bar.
    //
    // Drawn rather than added as a child, for the same reason the veils are: a Table lays out only its
    // cells, so a non-cell overlay would keep whatever bounds it was given and not follow the card when
    // the bar is laid out at a different size to the bank.
    public void setLocked(boolean locked) {
        this.lockBadge = locked ? MenuStyles.drawable(assets.skin(), LOCK_ART) : null;
    }

    public boolean isLocked() {
        return lockBadge != null;
    }

    // The opposite corner, for a packet that is the Imitater wearing this plant's coat.
    //
    // Its own field rather than the padlock's, so isLocked() keeps meaning "bolted to the bar" and a
    // card could in principle carry both. The mark is the Imitater's OWN seed packet, which says "this
    // Peashooter is really the Imitater" in one glance and needs no new card type to do it.
    private Drawable imitatedBadge;

    public void setImitated(Drawable badge) {
        this.imitatedBadge = badge;
    }

    // A slot on the bar with nothing in it yet.
    //
    // The same shipped frame as a real card, sunk back and left empty, so a bar of eight reads as eight
    // slots with three filled rather than as three cards floating in a gap. Deliberately not a
    // SeedCardActor: it has no packet, no price and nothing to click, and giving it one so it could
    // share a type would mean inventing a plant that is not there.
    //
    // `badge` may be null. When present it is a padlock, and the slot is one a mode has welded shut --
    // drawn rather than omitted, because "six slots" and "eight slots, two of them locked" are different
    // facts and the second is the one Locked Plants is about.
    public static Actor emptySlot(UiArt art, Drawable badge) {
        Table slot = new Table() {
            @Override
            public float getPrefWidth() {
                return CARD_WIDTH;
            }

            @Override
            public float getPrefHeight() {
                return CARD_HEIGHT;
            }
        };
        Drawable frame = art.stretchable(UiArt.SEED_PACKET, FRAME_BORDER);
        if (frame != null) {
            slot.setBackground(frame);
        }
        slot.pad(PAD_TOP, PAD_SIDE, PAD_BOTTOM, PAD_SIDE);
        slot.setColor(EMPTY_SLOT);
        slot.setSize(CARD_WIDTH, CARD_HEIGHT);
        if (badge != null) {
            Image padlock = new Image(badge);
            padlock.setScaling(Scaling.fit);
            slot.add(padlock).grow().pad(6f);
        }
        return slot;
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        if (boosted) {
            sheenClock += delta;
        }
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        super.draw(batch, parentAlpha);

        // The boost shimmer sits UNDER the veils: a packet that is recharging is still boosted, and the
        // cooldown wipe should dim both together.
        if (boosted) {
            float wave = (float) (0.5d + 0.5d * Math.sin(sheenClock * BOOST_SHEEN_HZ * Math.PI * 2d));
            sheenTint.a = BOOST_SHEEN_ALPHA * wave * parentAlpha;
            assets.solid(sheenTint).draw(batch, getX(), getY(), getWidth(), getHeight());
        }

        // Veils go on top of the card's own contents, so they dim the icon and the price together.
        if (cooldownFraction > 0f) {
            float covered = getHeight() * cooldownFraction;
            assets.solid(COOLDOWN_VEIL).draw(batch, getX(), getY() + getHeight() - covered,
                    getWidth(), covered);
        } else if (!affordable) {
            assets.solid(BROKE_VEIL).draw(batch, getX(), getY(), getWidth(), getHeight());
        }

        // Over the veils: a bolted-down seed is bolted down whether or not it is recharging.
        if (lockBadge != null) {
            float size = getWidth() * BADGE_FRACTION;
            lockBadge.draw(batch, getX() + getWidth() - size - BADGE_PAD,
                    getY() + getHeight() - size - BADGE_PAD, size, size);
        }
        if (imitatedBadge != null) {
            float size = getWidth() * BADGE_FRACTION;
            imitatedBadge.draw(batch, getX() + BADGE_PAD,
                    getY() + getHeight() - size - BADGE_PAD, size, size);
        }
    }
}
