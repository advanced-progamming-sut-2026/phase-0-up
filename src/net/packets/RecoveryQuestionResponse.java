package net.packets;

import net.Packet;

// The question INDEX, not the question text. Constants.SECURITY_QUESTIONS is the one list of
// questions, and User.getSecurityQuestion() already clamps a hand-edited or stale index rather than
// indexing it raw. Sending the index keeps that single source, and keeps the wording on the client
// where the rest of the player-facing text lives.
public record RecoveryQuestionResponse(boolean ok, String message, int securityQuestionIndex)
        implements Packet {
}
