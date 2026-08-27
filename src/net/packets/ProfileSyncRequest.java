package net.packets;

import net.Packet;
import utils.storage.records.UserRecord;

// "Here is my account as it now stands -- store it."
//
// Fired by RemoteBackend.flush(), which is what DatabaseManager.saveAll() delegates to on the client.
// That indirection is the whole reason the 23 existing saveAll() call sites -- across Login, Register,
// the greenhouse commands, the shop, QuestSystem, GameEngine and the rest -- need no edit at all: they
// keep calling saveAll(), and only what saveAll DOES changes.
//
// The whole record is sent, not a delta. The profile is a few kilobytes, saves are debounced, and a
// field-level diff would need a change-tracking layer on Profile that nothing else in the game wants.
public record ProfileSyncRequest(UserRecord user) implements Packet {
}
