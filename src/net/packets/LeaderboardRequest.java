package net.packets;

import net.Packet;

// `column` is an LbColumn enum NAME rather than the enum, so an older client asking for a column this
// server does not have gets a clean refusal instead of a decode failure that kills the connection.
public record LeaderboardRequest(String column, boolean ascending) implements Packet {
}
