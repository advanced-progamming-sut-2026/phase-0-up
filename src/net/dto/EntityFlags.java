package net.dto;

// Per-entity boolean state, packed into one int so a snapshot does not carry eight named booleans per
// entity ten times a second.
//
// Deliberately a bitset of constants rather than an EnumSet: EnumSet serialises as a JSON array of
// names, which is roughly twenty times the bytes of the integer it replaces, on the hottest field in
// the whole protocol.
public final class EntityFlags {

    private EntityFlags() { }

    public static final int NONE = 0;
    public static final int FROZEN = 1;
    public static final int EATING = 1 << 1;
    public static final int DYING = 1 << 2;
    public static final int BOOSTED = 1 << 3;
    // A zombie standing still and making sun -- I, Zombie draws these as the disco mech rather than as
    // the plain buckethead the model actually spawns. See ZombieRenderer.
    public static final int SUN_PRODUCER = 1 << 4;
    // Armour still worn. Three separate bits, not a count: Conehead, Buckethead and Brick are ONE
    // animation with three hideable hats, and the view switches parts on individually.
    public static final int ARMOUR_1 = 1 << 5;
    public static final int ARMOUR_2 = 1 << 6;
    public static final int ARMOUR_3 = 1 << 7;
    // A falling sun has not landed yet; the view drops it in from above the board rather than drawing
    // it at rest. Same distinction Sun.isFalling makes in the model.
    public static final int FALLING = 1 << 8;
    // A plant that has committed to an action -- a shot drawn back, a bloom starting -- but whose
    // effect has not appeared yet. Plant.isWindingUp(), which asks the plant's abilities, and a
    // mirrored plant's abilities never run. The view plays the attack clip off the rising edge of it.
    public static final int ACTING = 1 << 9;
    // A zombie off the ground: a Prospector riding its own dynamite back down the lane.
    //
    // The FLAG travels and the progress does not. A mirrored zombie's x is sent every tick and
    // interpolated on arrival, so the client already knows where along the arc it is; how HIGH it is
    // drawn is presentation the client times for itself from the rising edge of this bit, the same way
    // it times every other one-shot. One bit rather than a float, on the hottest field in the protocol.
    public static final int AIRBORNE = 1 << 10;

    public static boolean has(int flags, int flag) {
        return (flags & flag) != 0;
    }

    public static int with(int flags, int flag, boolean on) {
        return on ? (flags | flag) : (flags & ~flag);
    }
}
