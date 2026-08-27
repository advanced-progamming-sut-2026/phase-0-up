package net.packets;

import net.Packet;

// Creating an account. The server is what makes usernames globally unique -- DatabaseManager.addUser
// already refuses a duplicate and keyOf already matches case-insensitively, so moving the roster
// server-side makes that check global with no new rule.
//
// passwordHash and securityAnswerHash are hashed CLIENT-side with PasswordHasher, so no plaintext
// password ever leaves the machine that typed it. PasswordHasher is unsalted deterministic SHA-256,
// which is what makes that possible: the server compares hashes by string equality.
//
// The honest caveat, stated rather than hidden: a hash that is accepted as proof of identity IS a
// password equivalent on the wire. Anyone who can read the traffic can replay it. For a LAN course
// project that is an acceptable trade and strictly better than sending plaintext; it would not be over
// the open internet without TLS underneath.
public record RegisterRequest(
        String username,
        String passwordHash,
        String nickname,
        String email,
        String gender,
        int securityQuestionIndex,
        String securityAnswerHash) implements Packet {
}
