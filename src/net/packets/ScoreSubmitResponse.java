package net.packets;

import net.Packet;

// The server's verdict on a submitted score, and the authoritative record afterwards.
//
// previousBest is a BOXED Integer because null is a real answer: it means this player had never
// submitted a score before. The spec is explicit that someone who has not played the networked scoring
// game must not show a previous or fake score in the leaderboard's "My Point" column, and zero cannot
// carry that meaning -- zero is a score somebody can actually get. Same reasoning Profile.volume
// already documents for the same problem.
public record ScoreSubmitResponse(boolean accepted, int newBest, Integer previousBest)
        implements Packet {
}
