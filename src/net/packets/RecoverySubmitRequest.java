package net.packets;

import net.Packet;

// Steps two and three of password recovery, in one packet shape.
//
// newPasswordHash is NULLABLE, and that is the difference between them:
//
//   null      verify the answer only, change nothing. What the terminal conversation needs, so it can
//             say "Invalid answer!" before asking for a password it was never going to accept.
//   non-null  verify the answer AND set the password.
//
// The answer is re-checked on the reset, not merely trusted because a verify happened earlier. The two
// calls arrive on the same connection but nothing forces a client to make the first one, and a check
// that can be skipped by not calling it is not a check.
//
// Both hashes are computed client-side. The answer must be normalised through SecurityAnswer BEFORE
// hashing -- otherwise "Tehran " and "tehran" hash differently and a correct answer is rejected. The
// server cannot normalise on the player's behalf; it never sees the text.
public record RecoverySubmitRequest(String username, String answerHash, String newPasswordHash)
        implements Packet {

    public boolean isVerifyOnly() {
        return newPasswordHash == null;
    }
}
