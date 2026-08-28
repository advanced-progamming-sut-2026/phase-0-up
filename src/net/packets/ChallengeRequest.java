package net.packets;

import net.Packet;
import models.game.Faction;

// "I want to play THIS person." The spec's direct-challenge route.
//
// preferredFaction is a request, not a guarantee: if both players ask for the same side the server
// decides, so a match can always start rather than deadlocking on a preference.
public record ChallengeRequest(String targetUsername, Faction preferredFaction) implements Packet {
}
