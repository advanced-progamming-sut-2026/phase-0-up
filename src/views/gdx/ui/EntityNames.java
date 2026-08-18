package views.gdx.ui;

import java.util.Locale;
import java.util.Map;

// Names the player should read, for entities the data names after their class.
//
// The model identifies a zombie by its asset alias -- "ZombieArmor1", "ZombieDarkArmor3",
// "ZombieBeachFisherman" -- because that is what indexes the animation. Those are perfectly good keys
// and terrible labels: an almanac page headed "Armor1" tells a player nothing, and "Dark Armor3" reads
// like a debug string.
//
// This is presentation only. Nothing here goes back to the model, which continues to speak aliases.
public final class EntityNames {

    // The aliases whose readable name is not simply their spelling. Everything else falls through to
    // the generic tidy-up below, which handles the majority ("ZombieBeachOctopus" -> "Beach Octopus").
    private static final Map<String, String> ZOMBIES = Map.ofEntries(
            Map.entry("zombiedefault", "Browncoat Zombie"),
            Map.entry("zombiearmor1", "Conehead Zombie"),
            Map.entry("zombiearmor2", "Buckethead Zombie"),
            // "Brick", not "Helmet": ArmorType.BRICK is what the model calls it and
            // zombie_armor_brick_norm is what the animation calls it, so the almanac now agrees with
            // both instead of inventing a third name for the same hat.
            Map.entry("zombiearmor4", "Brick Zombie"),
            Map.entry("zombiedarkarmor3", "Knight Zombie"),
            Map.entry("zombiebeachfisherman", "Fisherman Zombie"),
            Map.entry("zombiebeachoctopus", "Octopus Zombie"),
            Map.entry("zombiebeachsnorkel", "Snorkel Zombie"),
            Map.entry("zombieicagedodo", "Dodo Rider Zombie"),
            Map.entry("zombieiceagedodo", "Dodo Rider Zombie"),
            Map.entry("zombieiceagehunter", "Hunter Zombie"),
            Map.entry("zombieiceagetroglobite", "Troglobite"),
            Map.entry("zombiedarkjuggler", "Jester Zombie"),
            Map.entry("zombiedarkking", "Zombie King"),
            Map.entry("zombiedarkimpdragon", "Imp Dragon Zombie"),
            Map.entry("zombiecrystalskull", "Crystal Skull Zombie"),
            Map.entry("zombiemodernallstar", "All-Star Zombie"),
            Map.entry("zombielostcityjane", "Excavator Zombie"),
            Map.entry("zombietombraiser", "Tomb Raiser Zombie"),
            Map.entry("zombiera", "Ra Zombie"),
            Map.entry("zombiepiano", "Piano Zombie"),
            Map.entry("zombiegargantuar", "Gargantuar"),
            Map.entry("zombieimp", "Imp"),
            Map.entry("zombiewizard", "Wizard Zombie"),
            Map.entry("zombieprospector", "Prospector Zombie"),
            Map.entry("zombieexplorer", "Explorer Zombie"),
            Map.entry("zombienewspaper", "Newspaper Zombie"),
            Map.entry("zombiearcade", "Arcade Zombie"));

    private EntityNames() { }

    // The name to print for a zombie alias. Falls back to a tidied version of the alias, so a zombie
    // added to the data tomorrow reads as "Beach Surfer" rather than as nothing at all.
    public static String zombie(String alias) {
        if (alias == null || alias.isBlank()) {
            return "";
        }
        String known = ZOMBIES.get(alias.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""));
        return known != null ? known : pretty(alias);
    }

    // Turns a data key into something readable: SUN_PRODUCER becomes "Sun Producer", and
    // ZombieBeachOctopus becomes "Beach Octopus". Both shapes appear -- categories and armour are
    // SCREAMING_SNAKE, aliases are CamelCase with a "Zombie" prefix nobody needs to read on a screen
    // that is entirely zombies.
    public static String pretty(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        String text = key.trim();
        if (text.length() > 6 && text.startsWith("Zombie") && Character.isUpperCase(text.charAt(6))) {
            text = text.substring(6);
        }
        text = text.replace('_', ' ').replaceAll("(?<=[a-z0-9])(?=[A-Z])", " ");
        StringBuilder out = new StringBuilder();
        for (String word : text.split("\\s+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return out.toString();
    }
}
