package net.packets;

import net.Packet;

// Changing your own password from the profile menu.
//
// Its own packet, and NOT a field on the profile sync, for the same reason a rename is not: it has a
// rule the server has to enforce. A sync applies whatever it is given to the account that sent it; a
// password change has to prove the OLD password first, and can be refused.
//
// Folding it into the sync would also mean every ordinary save -- a level ending, a plant being bought,
// twenty-three call sites' worth -- carried a password hash it had no business carrying. "Save my
// coins" and "change my password" stay separate operations.
//
// The account changed is always the one this connection signed in as; there is no username field here
// on purpose.
public record PasswordChangeRequest(String currentPasswordHash, String newPasswordHash)
        implements Packet {
}
