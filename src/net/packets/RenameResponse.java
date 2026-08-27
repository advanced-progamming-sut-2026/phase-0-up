package net.packets;

import net.Packet;
import utils.storage.records.UserRecord;

// The renamed account, or why the name could not be taken.
public record RenameResponse(boolean ok, String message, UserRecord user) implements Packet {
}
