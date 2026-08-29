package net.dto;

// One entity on the board, as the wire sees it.
//
// x and y are FLOATS, and that is load-bearing rather than tidy. A diagonal Rotobaga shot moves half a
// lane per tick and a falling sun has a precise height between rows; Entity.getY() is the rounded row
// it is filed under. Sending the rounded value would leave nothing between the lanes to interpolate,
// and the diagonals would cross the board sideways -- the exact failure EntityInterpolator documents.
//
// `type` is the registry name or alias the factories already take ("Peashooter", "ZombieGargantuar"),
// so the client rebuilds the entity through PlantFactory / ZombieFactory with no lookup table of its
// own.
public record EntityState(
        int netId,
        EntityKind kind,
        String type,
        float x,
        float y,
        int hp,
        int maxHp,
        int flags) {

    public boolean is(int flag) {
        return EntityFlags.has(flags, flag);
    }

    // ---- suns, which use hp and maxHp for something else entirely ----------------------------------
    //
    // A collectible has no health, so those two ints are free, and a sun needs exactly two numbers that
    // nothing else on this record carries: what it is WORTH (hp) and the height it comes to rest at
    // (maxHp, in hundredths of a row).
    //
    // The rest height is not cosmetic. `collect sun` names the TILE the model files a sun under, and
    // for one still in the air that is the tile it is falling towards -- so a client that does not know
    // it addresses the wrong row and the sun cannot be picked up at all. The view wants the fraction
    // too, to sit a landed sun at the height it actually landed at rather than on the lane line.
    //
    // Hundredths rather than a second float on this record: it is the hottest field in the protocol,
    // and 0.01 of a row is a third of a pixel.
    private static final float REST_HEIGHT_SCALE = 100f;

    public static int packRestHeight(double targetY) {
        return Math.round((float) targetY * REST_HEIGHT_SCALE);
    }

    public float restHeight() {
        return maxHp / REST_HEIGHT_SCALE;
    }
}
