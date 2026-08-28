package net.packets;

import net.Packet;
import models.social.ReactionKind;

// "Send this reaction to my opponent."
//
// A kind and an INDEX, never a string. The spec asks only for preset reactions -- three texts, three
// emojis, three stickers -- so carrying an index into a fixed catalogue makes an arbitrary message
// structurally impossible to send rather than merely discouraged, and there is no free-text field for
// anyone to abuse.
public record ReactionSend(ReactionKind kind, int index) implements Packet {
}
