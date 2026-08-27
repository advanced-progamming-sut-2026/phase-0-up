package net.packets;

import net.Packet;

public record UsernameCheckResponse(String username, boolean taken) implements Packet {
}
