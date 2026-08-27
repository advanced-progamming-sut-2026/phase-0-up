package net.packets;

import net.Packet;

import java.util.List;

// Who is signed in right now, for the lobby list. This is simply the key set of the server's
// authenticated-session map -- there is no separate presence table to keep in step with reality.
public record OnlineUsersResponse(List<String> usernames) implements Packet {
}
