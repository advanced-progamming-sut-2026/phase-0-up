package server.match;

import net.Envelope;
import net.PacketType;
import net.Protocol;
import net.dto.ChallengeRejectReason;
import net.dto.Faction;
import net.dto.MatchEndReason;
import net.packets.AckResponse;
import net.packets.ChallengeAnswer;
import net.packets.ChallengeDeclined;
import net.packets.ChallengeInvite;
import net.packets.ChallengeRejected;
import net.packets.ChallengeRequest;
import net.packets.MatchLeaveRequest;
import net.packets.MatchStart;
import net.packets.OnlineUsersRequest;
import net.packets.OnlineUsersResponse;
import net.packets.OpponentDisconnected;
import net.packets.QueueJoinRequest;
import net.packets.QueueLeaveRequest;
import net.packets.QueueStatus;
import server.AuthLevel;
import server.ClientSession;
import server.GameServer;
import server.PacketHandler;
import utils.storage.DatabaseManager;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

// Finding somebody to play against: the two routes the spec asks for, plus the bookkeeping that keeps
// them honest.
//
//   Direct challenge -- name a player. If the name is wrong or they are not online, say which; if they
//                       are, put a pop-up on their screen and wait for an answer.
//   Random match     -- join a queue. If somebody is already waiting, the two of you start at once;
//                       otherwise wait for the next person to ask.
//
// ## One lock, and why
//
// Challenges, the queue and the match registry are three views of the same question -- is this player
// available? -- and they are read and written from several connection threads at once. Guarding them
// separately would let a player be pulled out of the queue by one thread while another was accepting
// a challenge on their behalf, and end up in two matches. So every state change here happens under one
// lock. It is held for microseconds and never across a network write.
public final class MatchService {

    // A challenge waiting for an answer.
    private record Challenge(String id, ClientSession from, ClientSession to,
                             Faction challengerFaction, long expiresAtMillis) { }

    private final GameServer server;
    private final Object lock = new Object();

    // Pending challenges by id. A player may have at most one outgoing at a time -- see challenge().
    private final Map<String, Challenge> challenges = new ConcurrentHashMap<>();

    // The random-match queue, oldest first. A Deque rather than a List because the only two operations
    // are "add to the back" and "take from the front", and saying so stops anyone reaching into it.
    private final Deque<QueueEntry> queue = new ArrayDeque<>();

    private record QueueEntry(ClientSession session, Faction preferred) { }

    private final Map<String, Match> matches = new ConcurrentHashMap<>();

    // Sweeps expired challenges. One thread for the whole server: challenges are rare and the sweep is
    // a walk over a map that is almost always empty.
    private final ScheduledExecutorService sweeper =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "match-sweeper");
                thread.setDaemon(true);
                return thread;
            });

    public MatchService(GameServer server) {
        this.server = server;
    }

    public void registerHandlers() {
        register(PacketType.ONLINE_USERS_REQ, this::onOnlineUsers);
        register(PacketType.CHALLENGE_REQ, this::onChallenge);
        register(PacketType.CHALLENGE_ANSWER, this::onChallengeAnswer);
        register(PacketType.QUEUE_JOIN_REQ, this::onQueueJoin);
        register(PacketType.QUEUE_LEAVE_REQ, this::onQueueLeave);
        register(PacketType.MATCH_LEAVE_REQ, this::onMatchLeave);

        // A dropped socket has to undo everything this player was part of, or the lobby fills up with
        // ghosts: challenges nobody can answer, queue entries that never match, and matches whose
        // opponent is told nothing.
        server.addSessionClosedListener(this::onSessionClosed);

        sweeper.scheduleWithFixedDelay(this::expireChallenges, 1, 1, TimeUnit.SECONDS);
    }

    public void shutdown() {
        sweeper.shutdownNow();
    }

    private void register(PacketType type, PacketHandler handler) {
        // Everything here is AUTHENTICATED. Matchmaking is between named players, and a stranger who
        // has proved nothing has no name to be matched under.
        server.register(type, AuthLevel.AUTHENTICATED, handler);
    }

    // ---- the lobby ------------------------------------------------------------------------------

    private void onOnlineUsers(ClientSession session, Envelope envelope) {
        session.reply(envelope, new OnlineUsersResponse(availableOpponents(session)));
    }

    // Who could actually be challenged right now: signed in, not already playing, and not you.
    //
    // Filtered here rather than on the client, because "available" is a fact only the server holds --
    // a client cannot know that somebody on the list started a match a moment ago.
    private java.util.List<String> availableOpponents(ClientSession asker) {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (String name : server.onlineUsernames()) {
            ClientSession other = server.sessionOf(name);
            if (other == null || other == asker || other.match() != null) {
                continue;
            }
            names.add(name);
        }
        return names;
    }

    // ---- direct challenge -----------------------------------------------------------------------

    private void onChallenge(ClientSession session, Envelope envelope) {
        ChallengeRequest request = envelope.as(ChallengeRequest.class);
        String target = request.targetUsername() == null ? "" : request.targetUsername().trim();

        ChallengeRejectReason refusal = whyNotChallengeable(session, target);
        if (refusal != null) {
            session.reply(envelope, new ChallengeRejected(target, refusal));
            return;
        }

        Challenge challenge;
        synchronized (lock) {
            // Re-checked inside the lock. The check above produces the good error message; this is the
            // one that is actually true at the moment the challenge is created, because the target
            // could have started a match between the two.
            if (whyNotChallengeable(session, target) != null) {
                session.reply(envelope, new ChallengeRejected(target, ChallengeRejectReason.IN_MATCH));
                return;
            }
            // One outgoing challenge at a time, so a player cannot paper the lobby with invitations and
            // then accept several at once.
            withdrawOutgoing(session, false);
            challenge = new Challenge(UUID.randomUUID().toString(), session,
                    server.sessionOf(target), request.preferredFaction(),
                    System.currentTimeMillis() + Protocol.CHALLENGE_TIMEOUT_SECONDS * 1000L);
            challenges.put(challenge.id(), challenge);
        }

        // Sent outside the lock: a write can block on a slow peer, and nothing else may be waiting on
        // the lobby's lock while that happens.
        challenge.to().send(new ChallengeInvite(challenge.id(), session.username(),
                challenge.challengerFaction(), Protocol.CHALLENGE_TIMEOUT_SECONDS));
        session.reply(envelope, new AckResponse(true, "Challenge sent to " + target + "."));
    }

    // The spec asks for "an appropriate error" when the username is invalid or the user is offline.
    // Telling those two apart matters: one is a typo, the other is a "try again later".
    private ChallengeRejectReason whyNotChallengeable(ClientSession asker, String target) {
        if (target.isEmpty()) {
            return ChallengeRejectReason.NO_SUCH_USER;
        }
        if (target.equalsIgnoreCase(asker.username())) {
            return ChallengeRejectReason.SELF;
        }
        // The roster, not the online list -- otherwise a misspelled name and a sleeping friend produce
        // the same message, and the player has no idea which they are looking at.
        if (!DatabaseManager.getInstance().usernameExists(target)) {
            return ChallengeRejectReason.NO_SUCH_USER;
        }
        ClientSession other = server.sessionOf(target);
        if (other == null) {
            return ChallengeRejectReason.OFFLINE;
        }
        if (other.match() != null) {
            return ChallengeRejectReason.IN_MATCH;
        }
        return null;
    }

    private void onChallengeAnswer(ClientSession session, Envelope envelope) {
        ChallengeAnswer answer = envelope.as(ChallengeAnswer.class);
        Challenge challenge;
        Match started = null;

        synchronized (lock) {
            challenge = challenges.remove(answer.challengeId());
            // Gone means expired, withdrawn, or already answered. Not an error -- the pop-up may
            // simply have been clicked a moment after the timer ran out.
            if (challenge == null || challenge.to() != session) {
                session.reply(envelope, new AckResponse(false,
                        "That challenge is no longer open."));
                return;
            }
            if (!answer.accepted()) {
                // fall through to notify, outside the lock
            } else if (challenge.from().match() != null || session.match() != null) {
                session.reply(envelope, new AckResponse(false,
                        "One of you has already started another match."));
                return;
            } else {
                // The challenger gets the side they asked for; the accepter takes the other. Somebody
                // has to choose, and the person who did the inviting is the reasonable one.
                started = begin(challenge.from(), challenge.challengerFaction(), session);
            }
        }

        if (started != null) {
            announce(started);
            session.reply(envelope, new AckResponse(true, "Match on!"));
            return;
        }
        challenge.from().send(new ChallengeDeclined(session.username(), false));
        session.reply(envelope, new AckResponse(true, "Challenge declined."));
    }

    // Withdraw whatever this player has outstanding. `notifyTarget` is false when a newer challenge is
    // replacing it -- the invite pop-up is about to be superseded anyway.
    private void withdrawOutgoing(ClientSession from, boolean notifyTarget) {
        for (Iterator<Map.Entry<String, Challenge>> it = challenges.entrySet().iterator();
                it.hasNext();) {
            Challenge challenge = it.next().getValue();
            if (challenge.from() == from) {
                it.remove();
                if (notifyTarget) {
                    challenge.to().send(new ChallengeDeclined(from.username(), true));
                }
            }
        }
    }

    private void expireChallenges() {
        long now = System.currentTimeMillis();
        java.util.List<Challenge> expired = new java.util.ArrayList<>();
        synchronized (lock) {
            for (Iterator<Map.Entry<String, Challenge>> it = challenges.entrySet().iterator();
                    it.hasNext();) {
                Challenge challenge = it.next().getValue();
                if (challenge.expiresAtMillis() <= now) {
                    it.remove();
                    expired.add(challenge);
                }
            }
        }
        // Outside the lock, and the challenger is told rather than left waiting forever on a pop-up
        // that quietly closed on somebody else's screen.
        for (Challenge challenge : expired) {
            challenge.from().send(new ChallengeDeclined(challenge.to().username(), true));
        }
    }

    // ---- random match ---------------------------------------------------------------------------

    private void onQueueJoin(ClientSession session, Envelope envelope) {
        QueueJoinRequest request = envelope.as(QueueJoinRequest.class);
        Match started = null;
        int position;

        synchronized (lock) {
            if (session.match() != null) {
                session.reply(envelope, new AckResponse(false, "You're already in a match."));
                return;
            }
            removeFromQueue(session);

            QueueEntry waiting = takeNextWaiting(session);
            if (waiting != null) {
                // Whoever was waiting FIRST gets the side they asked for. Rewarding the wait is the
                // only tie-break here that is not arbitrary.
                started = begin(waiting.session(), waiting.preferred(), session);
            } else {
                queue.addLast(new QueueEntry(session, request.preferredFaction()));
            }
            position = queue.size();
        }

        if (started != null) {
            announce(started);
            return;
        }
        session.reply(envelope, new QueueStatus(true, position, position));
    }

    // The next usable person in the queue, discarding entries that have gone stale -- a player who
    // disconnected, or who started a match some other way while waiting. Without this, one abandoned
    // entry would sit at the head of the queue and pair with nobody forever.
    private QueueEntry takeNextWaiting(ClientSession asker) {
        while (!queue.isEmpty()) {
            QueueEntry candidate = queue.pollFirst();
            if (candidate.session() == asker) {
                continue;
            }
            if (candidate.session().connection().isOpen() && candidate.session().match() == null) {
                return candidate;
            }
        }
        return null;
    }

    private void onQueueLeave(ClientSession session, Envelope envelope) {
        synchronized (lock) {
            removeFromQueue(session);
        }
        session.reply(envelope, new QueueStatus(false, 0, queue.size()));
    }

    private void removeFromQueue(ClientSession session) {
        queue.removeIf(entry -> entry.session() == session);
    }

    // ---- starting and ending --------------------------------------------------------------------

    // Called under the lock. Assigns sides and registers the match; announcing happens afterwards,
    // outside, because it writes to two sockets.
    private Match begin(ClientSession chooser, Faction chooserFaction, ClientSession other) {
        Faction chosen = chooserFaction == null ? Faction.ZOMBIES : chooserFaction;
        ClientSession plants = chosen == Faction.PLANTS ? chooser : other;
        ClientSession zombies = chosen == Faction.PLANTS ? other : chooser;

        Match match = new Match(UUID.randomUUID().toString(), plants, zombies);
        matches.put(match.id(), match);
        plants.setMatch(match);
        zombies.setMatch(match);

        // Both are out of the lobby now: no queue entry, no outstanding invitations.
        removeFromQueue(plants);
        removeFromQueue(zombies);
        withdrawOutgoing(plants, true);
        withdrawOutgoing(zombies, true);
        return match;
    }

    // Each player gets a DIFFERENT MatchStart -- its own faction and its opponent's name are in it --
    // which is why this is two sends rather than a broadcast.
    //
    // The rosters and the board setup are left empty here. T3.6 builds VersusIZombieMode, and T3.7
    // fills these in from it; sending invented numbers now would be something to forget to replace.
    private void announce(Match match) {
        match.plants().send(startFor(match, Faction.PLANTS, match.zombies().username()));
        match.zombies().send(startFor(match, Faction.ZOMBIES, match.plants().username()));
    }

    private MatchStart startFor(Match match, Faction faction, String opponent) {
        return new MatchStart(match.id(), faction, opponent, 0,
                java.util.List.of(), java.util.List.of(), 0, 0, 0, 0, 0);
    }

    private void onMatchLeave(ClientSession session, Envelope envelope) {
        Match match = matchOf(session);
        if (match == null) {
            session.reply(envelope, new AckResponse(false, "You're not in a match."));
            return;
        }
        // Leaving is a forfeit, the same way abandoning a single-player level is a loss. Quitting is
        // not a way to avoid the result.
        finish(match, match.factionOf(session).opposite(), MatchEndReason.OPPONENT_LEFT);
        session.reply(envelope, new AckResponse(true, "You retreat from the lawn."));
    }

    // ---- disconnects ----------------------------------------------------------------------------

    private void onSessionClosed(ClientSession session) {
        Match match;
        synchronized (lock) {
            removeFromQueue(session);
            withdrawOutgoing(session, true);
            // Any invitation aimed AT this player is void too -- the pop-up is on a screen that no
            // longer exists, so the challenger would wait out the full timeout for nothing.
            challenges.entrySet().removeIf(entry -> {
                if (entry.getValue().to() == session) {
                    entry.getValue().from().send(new ChallengeDeclined(session.username(), true));
                    return true;
                }
                return false;
            });
            match = matchOf(session);
        }
        if (match == null) {
            return;
        }
        ClientSession opponent = match.opponentOf(session);
        if (opponent != null) {
            opponent.send(new OpponentDisconnected(session.username(),
                    Protocol.DISCONNECT_GRACE_SECONDS));
        }
        // Ended immediately for now. T3.7 gives a dropped player the grace period to reconnect into a
        // running board; until there is a board to come back to, waiting would only leave the other
        // player staring at nothing.
        finish(match, match.factionOf(session).opposite(), MatchEndReason.OPPONENT_LEFT);
    }

    private void finish(Match match, Faction winner, MatchEndReason reason) {
        if (match.end(winner, reason, 0, 0, 0)) {
            matches.remove(match.id());
        }
    }

    // ---- inspection -----------------------------------------------------------------------------

    public Match matchOf(ClientSession session) {
        return session.match();
    }

    public int pendingChallenges() {
        return challenges.size();
    }

    public int queueSize() {
        synchronized (lock) {
            return queue.size();
        }
    }

    public int liveMatches() {
        return matches.size();
    }
}
