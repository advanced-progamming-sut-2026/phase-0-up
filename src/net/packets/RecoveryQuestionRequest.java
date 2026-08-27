package net.packets;

import net.Packet;

// Step one of password recovery: confirm the account and email, and ask which security question it
// uses.
//
// The email travels WITH the request rather than being checked afterwards, because the check has to
// happen server-side. Verifying it on the client would mean the server handing over the account first,
// which is the whole thing this flow is arranged to avoid.
public record RecoveryQuestionRequest(String username, String email) implements Packet {
}
