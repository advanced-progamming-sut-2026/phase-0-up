package models.entities.zombies;

// The moves a Zomboss can make. One enum across all four bosses rather than one per boss, because the
// SCHEDULER is shared -- ZombossMode picks an attack at random every few seconds and does not care
// which machine it is driving -- and only the resolution differs (see ZombossAttacks).
//
// Each boss's own set lives on BossKind, so a Mammoth can never roll a Turbine.
public enum BossAttack {
    // Dark Ages -- the Zombot Dark Dragon.
    /** Fireball at a random tile: kills the plant, scorches the ground, drops an Imp Dragon. */
    FIREBALL,
    /** Breathes fire down both of its own rows: every plant in them burns, and so does the ground. */
    ROW_BURN,

    // Ancient Egypt -- the Zombot Sphinx-inator.
    /** Missile at a random tile: kills the plant, and two fresh graves rise elsewhere. */
    MISSILE,
    /** Charges the length of its two rows, flattening everything, then reverses back into place. */
    DASH,

    // Frostbite Caves -- the Zombot Tuskmaster.
    /** Slingshots an ice boulder at one tile, killing the plant standing there. */
    ICE_MISSILE,
    /** A freezing gale down two random rows, chilling every plant in them. */
    ICE_WIND,
    /** Glaciates a whole column and drops a frozen zombie into it. */
    FREEZE_COLUMN,

    // Big Wave Beach -- the Zombot Sharktronic Sub.
    /** Sends baby sharks up the water lanes to swallow a floating plant whole. */
    BABY_SHARKS,
    /** Runs its turbine: everything in its two rows is dragged toward the mouth and crushed. */
    TURBINE
}
