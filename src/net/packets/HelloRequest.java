package net.packets;

import net.Packet;

// First thing a client sends. The version check happens before authentication so a mismatched build is
// told so plainly, instead of failing later as an unexplained "unknown packet type".
public record HelloRequest(int protocolVersion, String clientBuild) implements Packet {
}
