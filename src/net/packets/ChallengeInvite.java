package net.packets;

import net.Packet;
import net.dto.Faction;

// The pop-up on the target's screen. Pushed, not requested -- it can arrive while they are sitting in
// any menu at all, which is why the client shows it on the Toasts overlay stage rather than on the
// current screen: that stage already draws above everything, including the lawn.
//
// expiresInSeconds is sent so the dialog can count down and close itself. A challenge that sits open
// forever pins the challenger in a waiting state they cannot leave.
public record ChallengeInvite(
        String challengeId,
        String fromUsername,
        Faction theirFaction,
        int expiresInSeconds) implements Packet {
}
