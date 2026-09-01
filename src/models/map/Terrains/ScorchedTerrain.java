package models.map.Terrains;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import utils.Constants;

// Ground the Dark Dragon has burnt: nothing can be planted here until it cools.
//
// The temporary sibling of CraterTerrain. A Doom-shroom's hole is permanent and needs no clock at
// all; the dragon's fire is the spec's "burns the ground, making it uncultivable for 4 seconds",
// which is a tile the player gets back -- and getting it back is the point, because a dragon that
// permanently deleted a tile every few seconds would leave nothing to defend with by the end of the
// fight.
//
// ## Why there is no update() here
//
// Terrain has no tick hook, and it does not need one. Cell.removeDestroyedTerrain already runs over
// every tile every frame (EnvironmentSystem.sweepDestroyedTerrain) and drops anything that reports
// itself destroyed -- so the whole expiry is isDestroyed() comparing the session's clock against the
// tick this was laid down on. Deriving it from the clock rather than counting down a field also means
// it cannot drift: nothing has to remember to tick it, and it expires correctly even on a tile that
// was somehow skipped for a frame.
public class ScorchedTerrain extends Terrain {

    // The spec's four seconds.
    public static final int BURN_SECONDS = 4;

    private final GameSession session;
    private final long expiresAtTick;

    public ScorchedTerrain(GameSession session) {
        this(session, BURN_SECONDS);
    }

    public ScorchedTerrain(GameSession session, int seconds) {
        this.plantable = false;
        // Distinct from the crater's 'o' and the grave's '#': the terminal map draws this too, and a
        // scorch that shared a symbol with a permanent hole would tell the player to give up on a tile
        // they get back in four seconds.
        this.symbol = 'x';
        this.session = session;
        this.expiresAtTick = (session == null ? 0L : session.getTimeTicks())
                + (long) seconds * Constants.TICKS_PER_SECOND;
    }

    // How much longer the ground stays hot, in ticks. For the "show tile status" readout, and for the
    // view, which fades the scorch out as it cools.
    public long remainingTicks() {
        if (session == null) {
            return 0L;
        }
        return Math.max(0L, expiresAtTick - session.getTimeTicks());
    }

    @Override
    public boolean isDestroyed() {
        return remainingTicks() <= 0L;
    }

    @Override
    public void effect(Zombie z, Plant p) {
        // Nothing. The fire has already done its damage; what is left is ground too hot to plant on,
        // and zombies walk over it exactly as they walk over a crater.
    }
}
