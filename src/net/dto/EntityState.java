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
}
