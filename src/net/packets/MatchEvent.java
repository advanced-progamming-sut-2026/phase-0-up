package net.packets;

import net.Packet;

// One line of narration, relayed from the server's own view layer.
//
// The model already publishes its narrative through GameSession.reportEvent rather than printing, and
// GameEngine drains that queue into whatever InGameRenderer it was given. The server's RelayRenderers
// turns each of those Results into one of these, so both players read the same commentary the
// single-player toasts show, written once, in the model.
//
// Two sentences never come through here: the spec-verbatim win and loss banners GameEngine hardcodes.
// Exactly one of the two players should see each of them, so RelayRenderers filters them out and each
// client renders its own from MatchOver.winner.
public record MatchEvent(boolean success, String text) implements Packet {
}
