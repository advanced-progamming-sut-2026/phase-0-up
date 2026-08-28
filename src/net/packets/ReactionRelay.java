package net.packets;

import net.Packet;
import models.social.ReactionKind;

// The server passing a reaction on to the other player. Named for what it is: the two clients never
// talk to each other, so every reaction goes out through the server and comes back down, which is the
// intermediary role the spec's architecture section describes.
//
// The sender's name travels with it so the popup can say who is taunting whom.
public record ReactionRelay(String fromUsername, ReactionKind kind, int index) implements Packet {
}
