package net.packets;

import net.Packet;

public record ChallengeAnswer(String challengeId, boolean accepted) implements Packet {
}
