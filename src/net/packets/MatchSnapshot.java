package net.packets;

import net.Packet;
import net.dto.EntityState;

import java.util.List;

// The authoritative board, once per simulation tick.
//
// Sent at Constants.TICKS_PER_SECOND (10 Hz), which is not a compromise -- it is the rate the whole
// game is written in. Every speed, cooldown and interval in the model is expressed in these ticks, and
// EntityInterpolator already exists to make exactly this cadence look like 60 fps motion. The client
// feeds each arriving snapshot through beginTick() / apply / endTick() and the smoothing it was built
// for in Phase 2 works unchanged.
//
// TWO sun banks, and they are not interchangeable. GameSession has a single sunAmount field, which
// stays the PLANT player's bank so every existing plant-cost check works untouched; the zombie
// player's bank lives on VersusIZombieMode. A shared pool would let either player spend the other's
// income.
//
// A full snapshot, not a delta. It is a few kilobytes at ~100 entities, it needs no acknowledgement
// protocol, and a client that misses one is corrected 100 ms later instead of drifting. The obvious
// refinement once this works -- plants do not move, so send them only when they change -- is a size
// optimisation, not a correctness one.
public record MatchSnapshot(
        long tick,
        int ticksRemaining,
        int sunPlants,
        int sunZombies,
        int plantFood,
        boolean[] brainEaten,
        List<EntityState> entities) implements Packet {
}
