package net.packets;

import net.Packet;

public record HelloResponse(boolean accepted, int serverVersion, String reason) implements Packet {
}
