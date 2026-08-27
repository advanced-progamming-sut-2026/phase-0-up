package net.packets;

import net.Packet;

// The generic yes/no answer, for requests whose only interesting result is whether they worked.
// Deliberately carries a message: every refusal in this game explains itself, and a bare `false` would
// leave the client inventing wording the model never chose.
public record AckResponse(boolean ok, String message) implements Packet {
}
