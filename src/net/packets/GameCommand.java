package net.packets;

import net.Packet;

// A player action, as the command string the game already understands.
//
// This is the payoff for CommandBridge. A click on a tile has ALWAYS produced
// "plant plant -t Sunflower -l (0, 2)" and posted it through GameEngine rather than calling the model
// directly, precisely so that the click and the typed command are one operation. That makes the
// client-to-server input protocol something this project already had: the string travels, and the
// server feeds it to the identical GameEngine.submitInGameCommand.
//
// Nothing is trusted about it. The server checks the sender's faction may issue this verb at all
// (MatchRunner's whitelist), and then the model applies its own rules -- cost, cooldown, occupied
// tile, the red line -- exactly as in single player. Neither check is duplicated on the client; the
// client's gating is convenience, the server's is the rule.
//
// clientTick is the tick the client believed it was on. Purely telemetry: it is never used to place
// the action in the past, because the server is authoritative and rewinding the board would desync the
// other player.
public record GameCommand(String text, long clientTick) implements Packet {
}
