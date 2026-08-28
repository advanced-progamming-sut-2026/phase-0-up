package server.match;

import net.Packet;
import models.game.Faction;
import net.dto.MatchEndReason;
import net.packets.MatchOver;
import server.ClientSession;

import java.util.concurrent.atomic.AtomicBoolean;

// Two players, one board.
//
// At this stage the match is the PAIRING and nothing more: who is playing, which side each is on, and
// how to reach both of them. The authoritative GameSession, the 10 Hz runner and the snapshot stream
// arrive in T3.7 and hang off this same object -- which is why it exists now rather than being
// invented then. The lobby needs something to mean "these two are busy", and a disconnect needs
// something to forfeit.
//
// ## Ending happens exactly once
//
// Both players can leave at the same instant, a socket can drop while the other player is already
// quitting, and (later) the tick thread can decide the match is over while a leave packet is in
// flight. Every one of those paths calls end(), so end() is guarded: the first caller wins and the
// rest are no-ops. Without that, both players would be told they won.
public final class Match {

    private final String id;
    private final ClientSession plants;
    private final ClientSession zombies;
    private final long startedAtMillis = System.currentTimeMillis();

    private final AtomicBoolean ended = new AtomicBoolean();

    // The authoritative simulation, or null for a match that has been paired but whose board has not
    // been built yet. Volatile because the lobby thread sets it and the tick thread reads it.
    private volatile MatchRunner runner;

    Match(String id, ClientSession plants, ClientSession zombies) {
        this.id = id;
        this.plants = plants;
        this.zombies = zombies;
    }

    public String id() {
        return id;
    }

    public ClientSession plants() {
        return plants;
    }

    public ClientSession zombies() {
        return zombies;
    }

    public boolean isOver() {
        return ended.get();
    }

    public MatchRunner runner() {
        return runner;
    }

    void setRunner(MatchRunner runner) {
        this.runner = runner;
    }

    public long startedAtMillis() {
        return startedAtMillis;
    }

    public ClientSession sessionOf(Faction faction) {
        return faction == Faction.PLANTS ? plants : zombies;
    }

    public Faction factionOf(ClientSession session) {
        if (session == plants) {
            return Faction.PLANTS;
        }
        if (session == zombies) {
            return Faction.ZOMBIES;
        }
        return null;
    }

    public ClientSession opponentOf(ClientSession session) {
        if (session == plants) {
            return zombies;
        }
        if (session == zombies) {
            return plants;
        }
        return null;
    }

    public boolean has(ClientSession session) {
        return session == plants || session == zombies;
    }

    // The same packet to both players. Snapshots and relayed narration go out this way; MatchStart
    // does not, because each client gets a different one -- its own faction is in it.
    public void broadcast(Packet packet) {
        plants.send(packet);
        zombies.send(packet);
    }

    // Announce the result and detach both players. Returns whether THIS call was the one that ended it,
    // so the caller can skip work (a profile write, a log line) that must not happen twice.
    //
    // The same MatchOver goes to both, naming the winning FACTION rather than saying "you won" -- each
    // client compares it against the side it was given at the start. That is what keeps the two
    // spec-verbatim banners landing on the right screen; from here both are true at once.
    public boolean end(Faction winner, MatchEndReason reason, int brainsEaten, int brainsTotal,
                       long elapsedTicks) {
        if (!ended.compareAndSet(false, true)) {
            return false;
        }
        broadcast(new MatchOver(winner, reason, brainsEaten, brainsTotal, elapsedTicks));
        plants.setMatch(null);
        zombies.setMatch(null);
        return true;
    }

    @Override
    public String toString() {
        return "match " + id + " (" + plants.username() + " plants vs "
                + zombies.username() + " zombies)";
    }
}
