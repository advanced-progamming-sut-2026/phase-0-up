package views.gdx.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import models.game.SeedPacket;
import views.gdx.core.Assets;

// One seed packet in the bank: what it plants, what it costs, and whether it can be used right now.
//
// The card renders three states and never decides any of them itself -- readiness comes from the
// packet's own cooldown, affordability from the wallet. Nothing here duplicates a rule; if the card
// says a plant is unavailable, the command would refuse it for exactly the same reason.
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

    private final Assets assets;
    private final SeedPacket packet;
    private final int cost;
    private final Label costLabel;

    private final com.badlogic.gdx.scenes.scene2d.utils.Drawable packetFrame;
    private boolean selected;
    private boolean affordable = true;
    private float cooldownFraction;   // 1 = just used, 0 = ready

    private final UiArt art;

    public SeedCardActor(Assets assets, UiArt art, SeedPacket packet, int cost, PlantIcon icon) {
        this.assets = assets;
        this.art = art;
        this.packet = packet;
        this.cost = cost;

        // The game's own pre-rendered packet: one image carrying the frame AND the plant picture.
        // Nothing is composited here, and the plant icon is deliberately unused when a packet exists --
        // drawing our own plant on top of one that already has it would double the art.
        // The card is composited from two shipped pieces, because the plant images are PORTRAITS on
        // transparent backgrounds -- drawn alone they float on the lawn with no card around them. The
        // blank packet from the same atlas supplies the frame and the portrait sits on it, which is how
        // the real card is put together.
        //
        // Nine-sliced rather than stretched: the frame is authored wider than it is tall (119x75) while
        // the bank wants taller cards, and stretching it outright would smear its border art. Slicing
        // keeps the corners intact and stretches only the middle.
        this.packetFrame = art.stretchable(UiArt.SEED_PACKET, 0.22f);
        setBackground(packetFrame);
        setColor(PANEL);
        setSize(CARD_WIDTH, CARD_HEIGHT);

        com.badlogic.gdx.graphics.g2d.TextureRegion portrait = art.packet(packet.getPlantType());
        if (portrait != null) {
            com.badlogic.gdx.scenes.scene2d.ui.Image image =
                    new com.badlogic.gdx.scenes.scene2d.ui.Image(
                            new com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable(portrait));
            image.setScaling(com.badlogic.gdx.utils.Scaling.fit);
            add(image).size(CARD_WIDTH - 10f, CARD_HEIGHT - 30f).padTop(3f).row();
        } else {
            // No shipped portrait for this plant: fall back to its live animation.
            add(icon).size(CARD_WIDTH - 12f, CARD_HEIGHT - 30f).padTop(3f).row();
        }

        this.costLabel = new Label(String.valueOf(cost), assets.skin());
        costLabel.setAlignment(Align.center);
        add(costLabel).padBottom(2f).center();
    }

    public String plantType() {
        return packet.getPlantType();
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
        // Tinting the frame, not swapping it: the packet art stays, it just lights up.
        setColor(selected ? PANEL_SELECTED : PANEL);
    }

    public boolean isSelected() {
        return selected;
    }

    // Refreshed every frame from live state. Cheap enough to do unconditionally, and doing it
    // unconditionally is what stops the card lying after a cheat or an upgrade changes the numbers.
    public void refresh(long currentTick, int sunAvailable) {
        this.affordable = sunAvailable >= cost;

        double remaining = packet.getRemainingCooldownSeconds(currentTick);
        double total = Math.max(1, packet.getCooldownDuration());
        this.cooldownFraction = (float) Math.max(0d, Math.min(1d, remaining / total));

        costLabel.setColor(affordable ? Color.WHITE : new Color(1f, 0.55f, 0.5f, 1f));
    }

    public boolean isUsable() {
        return affordable && cooldownFraction <= 0f;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        super.draw(batch, parentAlpha);

        // Veils go on top of the card's own contents, so they dim the icon and the price together.
        if (cooldownFraction > 0f) {
            float covered = getHeight() * cooldownFraction;
            assets.solid(COOLDOWN_VEIL).draw(batch, getX(), getY() + getHeight() - covered,
                    getWidth(), covered);
        } else if (!affordable) {
            assets.solid(BROKE_VEIL).draw(batch, getX(), getY(), getWidth(), getHeight());
        }
    }
}
