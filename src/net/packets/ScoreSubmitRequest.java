package net.packets;

import net.Packet;

// A finished scoring-game run. Sent from GameEngine.settleScoringGame, which is already the single
// place a Meow Point total is computed and compared against the player's best.
public record ScoreSubmitRequest(int meowPoints) implements Packet {
}
