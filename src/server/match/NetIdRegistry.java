package server.match;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

// Stable numbers for the things on the board, so a snapshot can say "this zombie" twice running.
//
// ## Why not Entity.getId()
//
// Because it does not identify anything. Every Projectile is constructed with id = 0
// (`super(type.toString(), 0, x, ...)`), plants carry their template id, and nothing assigns a unique
// value anywhere -- an id-keyed map collapses every pea in flight into one entry. EntityInterpolator
// hit this first and solved it the same way, which is the precedent being followed here: key on the
// object's IDENTITY, and hand out our own numbers.
//
// Ids are never reused. A zombie that dies and a zombie summoned on the same tile a second later must
// not share a number, or the client's reconciler will quietly move the old one instead of spawning the
// new one -- and the board would look right while being wrong.
//
// Single-threaded by contract: only the match's tick thread ever calls this, the same thread that owns
// the GameSession being described.
final class NetIdRegistry {

    private final Map<Object, Integer> ids = new IdentityHashMap<>();
    private int next = 1;

    int idOf(Object entity) {
        Integer existing = ids.get(entity);
        if (existing != null) {
            return existing;
        }
        int assigned = next++;
        ids.put(entity, assigned);
        return assigned;
    }

    // Drops everything not in this tick's board. Without it the map grows for the whole match: a
    // three-minute game with a busy lawn is tens of thousands of dead projectiles.
    void retainOnly(Set<Object> alive) {
        ids.keySet().retainAll(alive);
    }

    // An identity-based set, for building the argument to retainOnly. A HashSet would compare entities
    // with equals(), and two Peashooters are equal to each other for all the model cares.
    static Set<Object> newIdentitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    int size() {
        return ids.size();
    }
}
