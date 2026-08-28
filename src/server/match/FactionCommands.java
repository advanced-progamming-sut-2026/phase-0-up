package server.match;

import models.game.Faction;
import utils.regex.InGameRegex;

import java.util.EnumSet;
import java.util.Set;

// What each side of a versus match is allowed to do.
//
// This is where "a player cannot interfere with their opponent's gameplay" is actually enforced. The
// client hides the other side's HUD panel and its input processor refuses to build the other side's
// commands, but both of those are conveniences: the command channel is a string, and a string can be
// anything. The rule lives here, on the server, and it is checked before the engine sees a word of it.
//
// ## A whitelist, keyed on the parsed command
//
// Not a prefix check on the text -- "plant plant" vs "pluck plant" vs "release the nuke" would become
// a pile of startsWith calls that a slightly differently spaced command walks straight past. The text
// is matched against the same InGameRegex table GameEngine dispatches on, and the RESULT is looked up.
// Whatever the engine would have run is exactly what is checked.
//
// And a whitelist rather than a blacklist, which is the load-bearing half: a command added to
// InGameRegex later is refused by both factions until somebody deliberately lists it. The opposite
// arrangement fails open, and it fails open silently, in a competitive game.
public final class FactionCommands {

    private FactionCommands() { }

    // The plant player's whole vocabulary: put a plant down, dig one up, feed one, pick up sun.
    private static final Set<InGameRegex> PLANT_COMMANDS = EnumSet.of(
            InGameRegex.PLANT_SEED,
            InGameRegex.PLUCK_PLANT,
            InGameRegex.FEED_PLANT,
            InGameRegex.COLLECT_SUN);

    // The zombie player's. One verb, which is the shape of the mini-game.
    private static final Set<InGameRegex> ZOMBIE_COMMANDS = EnumSet.of(
            InGameRegex.SUMMON_ZOMBIE);

    // Deliberately allowed to NOBODY, and worth naming rather than leaving to the default:
    //
    //   every cheat        -- "release the nuke" wins a match outright
    //   advance time       -- the server owns the clock; one client fast-forwarding is a desync
    //   exit game          -- leaving is MATCH_LEAVE_REQ, which forfeits properly; this would end the
    //                         shared session out from under the other player
    //   swap / upgrade /
    //   bowl / vases       -- other mini-games' verbs, on a lawn that has none of their objects
    //   show map / status  -- terminal-only readouts; the server's map renderer draws nothing

    // The refusal to send back, or null if this command may run. Prose, because it is shown to the
    // player: a command that vanishes with no explanation reads as a broken game.
    public static String refusalFor(Faction faction, String text) {
        if (faction == null) {
            return "You're not in this match.";
        }
        InGameRegex command = parse(text);
        if (command == null) {
            return "The zombies didn't understand that one.";
        }
        if (allowed(faction).contains(command)) {
            return null;
        }
        if (allowed(faction.opposite()).contains(command)) {
            return faction == Faction.PLANTS
                    ? "That's the zombie player's move. You grow things; they eat them."
                    : "That's the plant player's move. You're on the undead side.";
        }
        return "Not in a versus match -- that one's for single player.";
    }

    public static boolean isAllowed(Faction faction, String text) {
        return refusalFor(faction, text) == null;
    }

    private static Set<InGameRegex> allowed(Faction faction) {
        return faction == Faction.PLANTS ? PLANT_COMMANDS : ZOMBIE_COMMANDS;
    }

    // Which in-game command this text is, or null if it is none of them. Every pattern is anchored and
    // they are mutually exclusive, so the first match is the only match.
    public static InGameRegex parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        for (InGameRegex command : InGameRegex.values()) {
            if (command.matches(text)) {
                return command;
            }
        }
        return null;
    }
}
