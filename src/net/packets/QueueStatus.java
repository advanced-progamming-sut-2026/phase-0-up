package net.packets;

import net.Packet;

// Where the player stands in the waiting queue, pushed whenever it moves. `waiting` false means they
// have left it (either cancelled, or matched -- MatchStart follows).
public record QueueStatus(boolean waiting, int position, int queueSize) implements Packet {
}
