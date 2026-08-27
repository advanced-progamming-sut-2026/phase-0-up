package net.dto;

// Why a match stopped. The winning FACTION is carried separately, because these two are not the same
// question: TIME_UP and HORDE_SPENT both mean the plant player won, and a client has to be able to say
// which without inferring it.
public enum MatchEndReason {
    BRAINS_EATEN,        // every brain gone -- the zombie player wins
    TIME_UP,             // the match clock expired with a brain still standing -- the plant player wins
    HORDE_SPENT,         // no living zombies and not enough sun for the cheapest -- the plant player wins
    OPPONENT_LEFT,       // forfeit or disconnect past the grace period
    SERVER_SHUTDOWN
}
