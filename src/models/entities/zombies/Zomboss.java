package models.entities.zombies;

import models.game.GameSession;

import java.util.ArrayList;
import java.util.List;

// The season boss: one enormous machine parked on the right of the lawn, straddling two rows.
//
// ## Why it is a Zombie and not a new kind of entity
//
// Everything that already works on a zombie has to work on this one -- a pea has to hit it, a
// Cherry Bomb has to burn it, the health bar has to drain, a Chomper has to be able to try. All of
// that is written against Zombie and reached through Row.getZombies(), so a boss that is not one
// would need every single one of those paths widened. Subclassing costs three overrides instead.
//
// ## Standing in two rows at once
//
// The spec's requirement is that plants in BOTH of its rows can shoot it, and "which zombies can this
// plant shoot" is answered everywhere in this codebase by Row.getZombies() -- so the boss is a member
// of both rows' lists. That is the whole mechanism: a pea fired from row 2 finds it in row 2's list, a
// pea from row 3 finds it in row 3's, and neither of them had to learn what a boss is.
//
// Being in two lists costs exactly three guards, and they are all here or named here:
//
//   * DOUBLE TICKING. CombatSystem.updateZombieStates walks the rows, so a two-row zombie is updated
//     twice a tick -- which would run its status timers down at double speed and, once it is dizzy,
//     halve the stun. The guard is `lastTickedAt` below: the second call in a tick returns at once.
//   * LANE RE-FILING. CombatSystem.reconcileZombieLanes moves any zombie whose movement.y disagrees
//     with the row holding it, which describes the lower half of the boss on every single tick. It
//     skips rowSpan() > 1 and the boss re-files itself in shiftTo().
//   * DOUBLE DEATH. CombatSystem.processDeaths would find the corpse in both rows and report it twice;
//     it now unlinks a multi-row zombie from every row at once, so the second row never sees it.
//
// ## Health sections
//
// Massive HP split into three equal bands. Crossing a band boundary staggers the machine: it stops
// attacking, stops shifting and stops summoning until it comes round, which is the player's window to
// pour damage in. Tracked here rather than in HealthComponent because it is a rule about THIS zombie
// and nothing else in the game has bands -- HealthComponent's layers are armour, which is a different
// idea (a layer absorbs damage; a band is just a line the total crosses).
public class Zomboss extends Zombie {

    // Three bands, per the spec's "boss health bar divided into 3 sections".
    public static final int SECTIONS = 3;

    private final BossKind kind;

    // How many band boundaries the health has already fallen through, so each one staggers it once.
    private int sectionsLost;

    // The tick this boss last ran its update on. -1 until the first one. See the class note.
    private long lastTickedAt = -1L;

    public Zomboss(int id, BossKind kind, int baseHp, double x, int topRow, GameSession session) {
        super(id, "boss", baseHp, new ArrayList<>(), kind.getAlias(),
                0,                       // it never bites: its damage is its attacks, run by ZombossMode
                utils.Constants.TICKS_PER_SECOND,
                0d,                      // and it never advances -- "stays in its columns"
                x, topRow,
                false,                   // no plant food from a boss; the level itself is the reward
                new ArrayList<>(), 0, false, session);
        this.kind = kind;
    }

    public BossKind getKind() {
        return kind;
    }

    // Two rows: the one it is filed under and the one below it. The spec's "occupies exactly 2 rows".
    @Override
    public int rowSpan() {
        return 2;
    }

    // Ticked once per game tick however many rows are holding it.
    @Override
    public void update(GameSession session) {
        long now = session == null ? -1L : session.getTimeTicks();
        if (now >= 0 && now == lastTickedAt) {
            return;
        }
        lastTickedAt = now;
        super.update(session);
    }

    // The rows this boss is standing in, top first. Used both to file it into the map and to ask
    // "is this attack aimed at my own rows".
    public List<Integer> occupiedRows() {
        List<Integer> rows = new ArrayList<>(rowSpan());
        int top = getMovement().getPositionY();
        for (int i = 0; i < rowSpan(); i++) {
            rows.add(top + i);
        }
        return rows;
    }

    public boolean occupiesRow(int row) {
        int top = getMovement().getPositionY();
        return row >= top && row < top + rowSpan();
    }

    // ---- health bands ---------------------------------------------------------------------------

    // How full the boss is, 0..1. What the boss bar draws and what the band arithmetic reads.
    public float healthFraction() {
        int max = getHealth().getMaxTotalHp();
        if (max <= 0) {
            return 0f;
        }
        return Math.max(0f, Math.min(1f, getHealth().getTotalHP() / (float) max));
    }

    // Bands already emptied, counting from the top. At full health 0; dead, SECTIONS.
    public int sectionsEmptied() {
        return Math.min(SECTIONS, (int) Math.floor((1f - healthFraction()) * SECTIONS + 1e-4f));
    }

    // Bands the player still has to grind through, for the readout.
    public int sectionsRemaining() {
        return Math.max(0, SECTIONS - sectionsEmptied());
    }

    // Has the health just fallen through a band boundary? True exactly once per boundary, so the
    // caller can stagger the machine without having to remember which bands it has already seen.
    //
    // Deliberately does not fire on the LAST boundary: that one is the boss dying, and a corpse
    // reeling from a stun it will never come out of is a second or two of the level looking hung.
    public boolean crossedSectionBoundary() {
        int emptied = Math.min(SECTIONS - 1, sectionsEmptied());
        if (emptied <= sectionsLost) {
            return false;
        }
        sectionsLost = emptied;
        return true;
    }
}
