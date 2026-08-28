package net.packets;

import net.Packet;
import net.dto.CardOffer;
import models.game.Faction;

import java.util.List;

// Everything a client needs to build the board before the first snapshot arrives.
//
// `yourFaction` is per-recipient: the two clients get DIFFERENT MatchStart packets from the same match.
// That one field drives the whole client-side split -- which HUD panel is built, which clicks the
// input processor will even turn into commands, and which end-of-match banner is shown.
//
// The rosters are sent rather than derived. The client's mirror GameSession does have a
// VersusIZombieMode on it, but its roster is built in onStart from a difficulty and a Random, and two
// machines must not each roll their own.
public record MatchStart(
        String matchId,
        Faction yourFaction,
        String opponentUsername,
        int matchDurationTicks,
        List<CardOffer> zombieRoster,
        List<CardOffer> plantSeedBank,
        int redLineColumn,
        int rows,
        int cols,
        int startingSunPlants,
        int startingSunZombies) implements Packet {
}
