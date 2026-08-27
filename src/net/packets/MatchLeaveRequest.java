package net.packets;

import net.Packet;

// Quitting mid-match. Treated as a forfeit, the same way GameEngine.abandonLevel treats leaving a
// single-player level as a loss -- leaving is not a way to avoid the result.
public record MatchLeaveRequest() implements Packet {
}
