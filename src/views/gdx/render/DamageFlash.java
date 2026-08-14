package views.gdx.render;

import java.util.IdentityHashMap;
import java.util.Map;

// The white flash an entity gives off when it is hit.
//
// The model has no per-hit event to listen for: damage is applied inside CombatSystem and an ability's
// own code, and nothing is queued or narrated for a single point of HP. Adding such an event would put
// a purely cosmetic concern into the model and fire it dozens of times a second during a heavy wave.
//
// So the view watches instead. A drop in health IS the hit -- the same reasoning that makes a
// projectile's disappearance the impact in GameRenderer. That also means the flash covers every source
// of damage for free: peas, explosions, a radioactive sun, a zombie's bite, lawn mowers, and anything
// a later phase adds without it having to remember to announce itself.
//
// Keyed by identity, never by an int id: every Projectile is constructed with id = 0 and entities have
// no stable key of their own, which is the same trap EntityInterpolator documents.
public final class DamageFlash {

    // How long a hit stays visible. Long enough to register at 60fps, short enough that a plant under
    // sustained fire strobes rather than glowing permanently white.
    private static final float FLASH_SECONDS = 0.14f;

    // Peak brightness added on top of the sprite. Full white washes the art out completely and loses
    // which entity was hit; this reads as a flash while the plant or zombie stays recognisable.
    private static final float PEAK = 0.75f;

    private static final class Entry {
        int lastHp = Integer.MIN_VALUE;
        float remaining;
        boolean seenThisFrame;
    }

    private final Map<Object, Entry> tracked = new IdentityHashMap<>();

    // Call once per entity per frame, with its current health. Returns 0 when it is not flashing.
    //
    // The first sighting of an entity only records its health: without that, every plant would flash
    // on the frame it was planted and every zombie on the frame it walked on.
    public float intensity(Object entity, int currentHp, float delta) {
        Entry entry = tracked.computeIfAbsent(entity, key -> new Entry());
        entry.seenThisFrame = true;

        if (entry.lastHp == Integer.MIN_VALUE) {
            entry.lastHp = currentHp;
            return 0f;
        }
        if (currentHp < entry.lastHp) {
            // Restarted rather than accumulated, so rapid hits keep flashing instead of saturating.
            entry.remaining = FLASH_SECONDS;
        }
        entry.lastHp = currentHp;

        if (entry.remaining <= 0f) {
            return 0f;
        }
        entry.remaining = Math.max(0f, entry.remaining - delta);
        // Fades out over its life. A constant-brightness flash that simply vanishes reads as a glitch.
        return PEAK * (entry.remaining / FLASH_SECONDS);
    }

    // Drops entities that were not drawn this frame -- eaten plants, dead zombies, a whole board on
    // restart. Without it the map grows for the life of the level and holds those objects from being
    // collected.
    public void sweep() {
        tracked.entrySet().removeIf(e -> !e.getValue().seenThisFrame);
        for (Entry entry : tracked.values()) {
            entry.seenThisFrame = false;
        }
    }
}
