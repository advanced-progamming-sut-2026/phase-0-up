package net.packets;

import net.Packet;
import utils.storage.records.UserRecord;

// A successful login carries the WHOLE account, profile included. That single fact is what satisfies
// the spec's cross-device requirement: coins, gems, unlocked plants and campaign progress arrive from
// the server on every login, so signing in on another machine shows the same account.
//
// The client feeds `user` through UserRecord.toUser() and then runs the same two repairs LoginCommand
// already runs on a profile that came off disk -- ensureStartingPlants() and
// LevelInitializer.attachCampaign() -- because a record deserialised from JSON has skipped the Profile
// constructor either way, and for the identical reason.
public record LoginResponse(boolean ok, String message, UserRecord user, String sessionToken)
        implements Packet {
}
