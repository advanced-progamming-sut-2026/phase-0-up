package models.entities.projectiles;

import models.entities.zombies.Components.StateComponent;

/**
 * The elemental nature of a source of damage.
 *
 * <p>This is intentionally separate from how the damage is delivered (see {@link Trajectory})
 * so the two axes can vary independently &mdash; e.g. a projectile can be both {@code LOBBED}
 * and {@code ICE}, which the old single {@code DamageType} enum could not express.</p>
 *
 * <p>Each constant owns the combat semantics that are intrinsic to the element itself:
 * whether it seeps past armor ({@link #piercesBaseArmor()}) and the status effect it inflicts
 * on hit ({@link #applyOnHit(StateComponent)}). Target-specific resistances (a grave shrugging
 * off poison, frozen terrain melting in fire) stay on the target that defines them.</p>
 */
public enum Element {
    NEUTRAL(false),
    FIRE(false),
    ICE(false) {
        @Override
        public void applyOnHit(StateComponent state) {
            state.applyChill(CHILL_DURATION_TICKS);
        }
    },
    POISON(true),
    BUTTER(false) {
        @Override
        public void applyOnHit(StateComponent state) {
            state.applyButter(BUTTER_DURATION_TICKS);
        }
    };

    private static final int CHILL_DURATION_TICKS = 100;
    private static final int BUTTER_DURATION_TICKS = 80;

    private final boolean piercesBaseArmor;

    Element(boolean piercesBaseArmor) {
        this.piercesBaseArmor = piercesBaseArmor;
    }

    /**
     * Whether this element bypasses outer armor layers and applies straight to the zombie's
     * base body (poison seeps through buckets, helmets and screen doors).
     */
    public boolean piercesBaseArmor() {
        return piercesBaseArmor;
    }

    /**
     * Applies this element's on-hit status effect to a struck zombie's state. Most elements
     * inflict nothing; {@code ICE} chills the zombie and {@code BUTTER} stuns it in place.
     */
    public void applyOnHit(StateComponent state) {
        // default: no on-hit status effect
    }
}
