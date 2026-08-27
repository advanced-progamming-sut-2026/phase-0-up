package net.packets;

import models.leaderboard.LeaderboardEntry;
import net.Packet;

import java.util.List;

// The whole board, already sorted, straight from the server's user roster.
//
// LeaderboardEntry crosses the wire verbatim: it is already "one immutable row ... reads only plain
// scalar progress off the Profile, so no live game object is ever touched here". Built for
// persistence-safety, it turns out to be exactly a network DTO, and re-declaring its seven fields here
// would only create something to drift.
//
// The sort itself is the existing LeaderboardSystem.sortBy, run server-side against the server's
// DatabaseManager -- so the ordering rules, including the username tie-break, stay written once.
public record LeaderboardResponse(List<LeaderboardEntry> rows, int yourRank) implements Packet {
}
