package net.packets;

import net.Packet;

// The other player's socket dropped. Sent immediately so the remaining player is not left staring at a
// board that has stopped moving with no explanation; the match itself is not ended until the grace
// period expires without a reconnect, at which point MatchOver follows with OPPONENT_LEFT.
public record OpponentDisconnected(String username, int graceSeconds) implements Packet {
}
