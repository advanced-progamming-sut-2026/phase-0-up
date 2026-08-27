package net.packets;

import net.Packet;

public record UsernameCheckRequest(String username) implements Packet {
}
