package net.packets;

import net.Packet;
import models.game.Faction;
import net.dto.MatchEndReason;

// How the match ended, said once, to both players.
//
// `winner` is a FACTION, not "you won" -- the same packet goes to both clients and each compares it
// against the faction it was given in MatchStart. That is what keeps the two spec-verbatim banners
// ("Dear humanz, zis is not done yet..." / "The zombie ate your brain; LOSER!!!") landing on the right
// screen: the server never picks one, because from its seat both are true at once.
public record MatchOver(
        Faction winner,
        MatchEndReason reason,
        int brainsEaten,
        int brainsTotal,
        long elapsedTicks) implements Packet {
}
