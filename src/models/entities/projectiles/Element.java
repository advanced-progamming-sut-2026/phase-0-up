package models.entities.projectiles;

/**
 * The elemental nature of a source of damage.
 *
 * <p>This is intentionally separate from how the damage is delivered (see {@link Trajectory})
 * so the two axes can vary independently &mdash; e.g. a projectile can be both {@code LOBBED}
 * and {@code ICE}, which the old single {@code DamageType} enum could not express.</p>
 *
 * <p>Each constant owns the combat semantics that are intrinsic to the element itself.
 * Target-specific resistances (a grave shrugging off poison, frozen terrain melting in fire)
 * stay on the target that defines them.</p>
 */
public enum Element {
    NEUTRAL(false),
    FIRE(false),
    ICE(false),
    POISON(true);

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
}
