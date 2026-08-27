package net.packets;

import net.Packet;

// Sent back to the CHALLENGER when the invite is refused or times out. Separate from ChallengeRejected
// on purpose: that one means the challenge never got delivered, this one means a person said no, and
// the two deserve different wording.
public record ChallengeDeclined(String byUsername, boolean timedOut) implements Packet {
}
