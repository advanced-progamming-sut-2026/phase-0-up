package net.packets;

import net.Packet;

// Changing your own username.
//
// Its own packet rather than a field on the profile sync, because a rename is the one profile edit
// with a rule attached that only the server can enforce: the new name must not belong to somebody
// else. A sync applies whatever it is given to the account that sent it; a rename can be REFUSED, and
// the caller has to hear about it.
//
// The account renamed is always the one this connection signed in as -- there is no "which account"
// field here, deliberately.
public record RenameRequest(String newUsername) implements Packet {
}
