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

    public static boolean has(int flags, int flag) {
        return (flags & flag) != 0;
    }

    public static int with(int flags, int flag, boolean on) {
        return on ? (flags | flag) : (flags & ~flag);
    }
}
