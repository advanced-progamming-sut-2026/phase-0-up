package net.packets;

import net.Packet;

public record LoginRequest(String username, String passwordHash, boolean stayLoggedIn)
        implements Packet {
}
