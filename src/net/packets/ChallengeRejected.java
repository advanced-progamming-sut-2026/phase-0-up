package net.packets;

import net.Packet;
import net.dto.ChallengeRejectReason;

// The challenge never reached anyone. Covers the spec's "if the username is invalid or the respective
// user is offline, an appropriate error must be displayed" -- the reason is an enum so the client can
// word each case in the game's own voice instead of echoing a server string.
public record ChallengeRejected(String targetUsername, ChallengeRejectReason reason)
        implements Packet {
}
