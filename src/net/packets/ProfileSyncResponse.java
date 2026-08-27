package net.packets;

import net.Packet;
import utils.storage.records.UserRecord;

// The stored account echoed back. The client rebases on this rather than assuming its own write won,
// so a field the server clamps or refuses (a Meow Point best that did not actually beat the record)
// does not silently diverge between the two sides.
public record ProfileSyncResponse(boolean ok, String message, UserRecord user) implements Packet {
}
