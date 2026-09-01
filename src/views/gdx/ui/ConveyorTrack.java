package views.gdx.ui;

import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;

import java.util.ArrayList;
import java.util.List;

// The nuts riding Wall-nut Bowling's belt.
//
// ## Why this is not a Table
//
// It was one, top-aligned, rebuilt from scratch whenever the mode's list changed. Two things followed
// from that, and both of them are the reason the belt never looked like a belt:
//
//   * A Table SNAPS. A nut delivered five seconds into the level simply existed, at its slot, on the
//     frame the list changed -- while the slats scrolled continuously behind it. The one moving thing on
//     screen was the background of the thing that was supposed to be moving.
//   * Rebuilding discards every actor. A card that had been on the belt all along was destroyed and
//     recreated whenever any OTHER card was added or removed, so there was no object left to animate
//     even if something had wanted to: every card was one frame old.
//
// So the cards are placed by hand here and reconciled against the mode's list rather than rebuilt from
// it. A card that stays on the belt keeps its actor, keeps its position, and rides up to wherever the
// queue has left room -- which is what a conveyor does.
//
// ## Which way, and how fast
//
// UP, at exactly ConveyorBelt.PIXELS_PER_SECOND. Sharing the constant is the whole point rather than a
// tidiness: cargo moving at a different rate to the surface under it is the one thing a conveyor cannot
// do, and two numbers that merely happen to match today would drift the first time either is tuned.
//
// Index 0 is the FRONT of the queue and sits at the top -- the mode appends deliveries, so the oldest
// nut has ridden furthest. New arrivals start below the bottom edge and walk on, which is why the whole
// track is clipped by the belt column around it.
final class ConveyorTrack extends WidgetGroup {

    private final float slotWidth;
    private final float slotHeight;

    // The kinds currently shown, parallel to `riders`. Kept so the mode's list can be diffed against
    // what is on screen instead of replacing it.
    private final List<models.entities.plants.bowling.BowlingKind> shown = new ArrayList<>();
    private final List<SeedCardActor> riders = new ArrayList<>();

    ConveyorTrack(float slotWidth, float slotHeight) {
        this.slotWidth = slotWidth;
        this.slotHeight = Math.max(1f, slotHeight);
        setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.childrenOnly);
    }

    // Brings the belt into line with the mode's list, creating a card only for a genuinely new nut.
    //
    // The reconciliation is a two-pointer walk, which is enough because of how the mode moves the list:
    // deliveries are APPENDED and a bowled nut is a single removal, so the new list is always the old
    // one with some entries deleted and some added at the end. Order is preserved in both, so a card is
    // matched to the first still-unmatched entry of its own kind -- and a player bowling one of three
    // Bowling nuts therefore takes the one at the front, which is the one they were looking at.
    void reconcile(List<models.entities.plants.bowling.BowlingKind> belt,
                   java.util.function.Function<models.entities.plants.bowling.BowlingKind,
                           SeedCardActor> factory) {
        if (belt.equals(shown)) {
            return;
        }
        List<SeedCardActor> kept = new ArrayList<>();
        int old = 0;
        for (models.entities.plants.bowling.BowlingKind kind : belt) {
            while (old < shown.size() && shown.get(old) != kind) {
                riders.get(old).remove();
                old++;
            }
            if (old < shown.size()) {
                kept.add(riders.get(old));
                old++;
            } else {
                kept.add(arrive(factory.apply(kind)));
            }
        }
        for (; old < shown.size(); old++) {
            riders.get(old).remove();
        }
        riders.clear();
        riders.addAll(kept);
        shown.clear();
        shown.addAll(belt);
    }

    // The cards currently on the belt, for the HUD's per-frame refresh pass. Appended rather than
    // assigned, because that pass walks one list holding every card on screen.
    void collectInto(List<SeedCardActor> into) {
        into.addAll(riders);
    }

    // A new nut is placed just BELOW the belt and rides on, rather than appearing at its slot. The
    // delivery is a moment worth seeing: it is the mode's one piece of feedback that more is coming.
    private SeedCardActor arrive(SeedCardActor card) {
        addActor(card);
        card.setSize(slotWidth, slotHeight);
        card.setPosition(0f, -slotHeight);
        return card;
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        float step = ConveyorBelt.PIXELS_PER_SECOND * delta;
        for (int i = 0; i < riders.size(); i++) {
            SeedCardActor card = riders.get(i);
            card.setSize(slotWidth, slotHeight);
            // Slot 0 is flush with the top; each one behind it hangs a slot lower.
            float target = getHeight() - (i + 1) * slotHeight;
            float y = card.getY();
            // Never overshoot: a card that stepped past its slot would jitter around it forever at
            // sixty frames a second. Clamped in both directions, though only the upward one can
            // actually happen -- nothing is ever inserted ahead of a card already on the belt.
            card.setY(y < target ? Math.min(target, y + step) : Math.max(target, y - step));
        }
    }

    // The belt is a fixed column: the slats behind it size the stack, and the cards ride inside
    // whatever that leaves. Answering zero would let the Stack collapse the track to nothing.
    @Override
    public float getPrefWidth() {
        return slotWidth;
    }

    @Override
    public float getPrefHeight() {
        return slotHeight;
    }
}
