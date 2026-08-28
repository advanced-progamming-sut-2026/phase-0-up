package net.packets;

import net.Packet;
import models.game.Faction;

// The spec's random-match route: join the waiting queue. If someone is already waiting the server
// pairs them immediately; otherwise this player waits until the next request arrives.
public record QueueJoinRequest(Faction preferredFaction) implements Packet {
}
