package net.packets;

import net.Packet;
import utils.storage.records.UserRecord;

// The created account, or why it was refused.
//
// UserRecord is carried verbatim rather than re-modelled. It is already documented as a "plain-data
// snapshot of a User ... free of any live game object" -- which is exactly the contract a packet needs,
// so it is already a wire DTO and re-declaring its fields here would only create something to drift.
public record RegisterResponse(boolean ok, String message, UserRecord user) implements Packet {
}
