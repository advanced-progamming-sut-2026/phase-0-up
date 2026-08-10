package views.gdx.sprite;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import pvz.libpvz.pam.PamPlayer;
import pvz.libpvz.textures.TextureBank;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

// Turns a model-level name ("Wall-nut", "ZombieTombRaiser") into something drawable.
//
// pvz-assets/animations.json lists all 1458 animations with their canvas and clip names, and its
// `path` field is exactly what PamPlayer wants -- it resolves paths against IMAGES/, and the file
// stores them as "768/INITIAL/PLANT/PEASHOOTER/PEASHOOTER.PAM". So no path guessing is needed; the
// only real work is matching OUR names to THEIR names.
public final class SpriteRegistry {

    private final PamPlayer player;
    private final TextureBank bank;

    // animation name -> its entry in animations.json
    private final Map<String, AnimationEntry> byName = new HashMap<>();
    // normalised animation name -> entry, for the ~87% of plants that match on shape alone
    private final Map<String, AnimationEntry> byNormalisedName = new HashMap<>();

    private final Map<String, EntitySprite> cache = new HashMap<>();
    // Everything that fell back to a still image, reported once at the end of loading rather than
    // scattered through the log.
    private final Set<String> unresolved = new TreeSet<>();

    // Names that normalisation cannot reach, derived by cross-referencing data/plants.json and
    // data/zombie-data/zombies.json against animations.json. Two things to know:
    //
    //  * The armor zombies (ZombieArmor1/2/4, ZombieDarkArmor3) have NO animation of their own. In
    //    PvZ2 a conehead is the base zombie with a cone PART enabled, which is precisely what libPVZ's
    //    visibility maps express -- so they map to the base body and ArmorVisibility supplies the hat.
    //  * A few plants (Rotobaga, Cat-tail, Iceberg Lettuce, Kernel-pult, Pierce-mint) are simply not
    //    in this asset dump. They resolve to a still image and are listed at startup.
    private static final Map<String, String> NAME_OVERRIDES = buildOverrides();

    private static Map<String, String> buildOverrides() {
        Map<String, String> m = new LinkedHashMap<>();

        // --- zombies -------------------------------------------------------------------------------
        m.put("ZombieDefault", "ZOMBIE_TUTORIAL");
        m.put("ZombieArmor1", "ZOMBIE_TUTORIAL");        // cone   -> base body + armor part
        m.put("ZombieArmor2", "ZOMBIE_TUTORIAL");        // bucket -> base body + armor part
        m.put("ZombieArmor4", "ZOMBIE_TUTORIAL");        // helmet -> base body + armor part
        m.put("ZombieDarkArmor3", "ZOMBIE_DARK_KING");   // knight pauldron
        m.put("ZombieGargantuar", "GARGANTUAR");
        m.put("ZombieImp", "GARGANTUAR_IMP");
        m.put("ZombieRa", "ZOMBIE_EGYPT_RA");
        m.put("ZombieTombRaiser", "ZOMBIE_EGYPT_TOMBRAISER");
        m.put("ZombieIceAgeDodo", "ZOMBIE_ICEAGE_DODORIDER");
        m.put("ZombieBeachSnorkel", "ZOMBIE_BEACH_SNORKELER");
        m.put("ZombieDarkJuggler", "ZOMBIE_DARK_JESTER");   // "juggler" in our data, Jester in the art
        m.put("ZombieWizard", "ZOMBIE_DARK_WIZARD");
        m.put("ZombieCrystalSkull", "ZOMBIE_LOSTCITY_CRYSTALSKULL");
        m.put("ZombieNewspaper", "ZOMBIE_MODERN_NEWSPAPER");
        m.put("ZombieArcade", "ZOMBIE_80S_ARCADE");

        // --- plants --------------------------------------------------------------------------------
        m.put("Twin Sunflower", "SUNFLOWER_TWIN");
        m.put("Mega Gatling Pea", "MEGAGATLING");
        m.put("Phat Beet", "PHATBEETS");
        return m;
    }

    public SpriteRegistry(PamPlayer player, TextureBank bank, FileHandle assetRoot) {
        this.player = player;
        this.bank = bank;
        loadAnimationIndex(assetRoot.child("animations.json"));
    }

    private void loadAnimationIndex(FileHandle file) {
        if (!file.exists()) {
            Gdx.app.error("SpriteRegistry", "animations.json not found at " + file.path()
                    + " -- every entity will fall back to a still image.");
            return;
        }
        JsonValue root = new JsonReader().parse(file);
        JsonValue list = root.get("animations");
        for (JsonValue node = list.child; node != null; node = node.next) {
            String name = node.getString("name", null);
            String path = node.getString("path", null);
            if (name == null || path == null) {
                continue;
            }
            Set<String> clips = new LinkedHashSet<>();
            Map<String, Float> durations = new HashMap<>();
            JsonValue clipNode = node.get("clips");
            if (clipNode != null) {
                for (JsonValue c = clipNode.child; c != null; c = c.next) {
                    clips.add(c.name);
                    // animations.json stores each clip's length in seconds; it is the only source for
                    // it, and without it nothing can loop or clamp correctly.
                    durations.put(c.name, c.asFloat());
                }
            }
            AnimationEntry entry = new AnimationEntry(name, path, clips, durations);
            keepBetter(byName, name, entry);
            keepBetter(byNormalisedName, normalise(name), entry);
        }
        Gdx.app.log("SpriteRegistry", "indexed " + byName.size() + " animations");
    }

    // animations.json contains 1458 entries but only ~1357 distinct names: the lawn Peashooter
    // (768/INITIAL/PLANT/PEASHOOTER) and the cutscene one (768/FULL/NPC/PEASHOOTER) are both simply
    // named "PEASHOOTER". Last-write-wins picked the NPC, whose clips are peashooter_talk/shout -- so
    // the plant silently vanished from the lawn. Resolve collisions by what the path says the asset is
    // FOR, not by which happened to be listed later.
    private static void keepBetter(Map<String, AnimationEntry> index, String key, AnimationEntry entry) {
        AnimationEntry existing = index.get(key);
        if (existing == null || pathScore(entry.path) > pathScore(existing.path)) {
            index.put(key, entry);
        }
    }

    private static int pathScore(String path) {
        String p = path.toUpperCase();
        int score = 0;
        if (p.contains("/PLANT/") || p.contains("/ZOMBIE/") || p.contains("/ZOMBIES/")) {
            score += 100;   // the actual gameplay actor
        }
        if (p.contains("/NPC/")) {
            score -= 100;   // Crazy Dave / Penny cutscene puppets that share a plant's name
        }
        if (p.contains("/DEV/") || p.contains("/WORLDMAP/") || p.contains("/MINIGAME/")) {
            score -= 50;
        }
        return score;
    }

    // The single entry point. Never returns null.
    public EntitySprite get(String entityName) {
        if (entityName == null || entityName.isBlank()) {
            return new StaticEntitySprite(null);
        }
        EntitySprite cached = cache.get(entityName);
        if (cached != null) {
            return cached;
        }
        EntitySprite sprite = build(entityName);
        cache.put(entityName, sprite);
        return sprite;
    }

    private EntitySprite build(String entityName) {
        AnimationEntry entry = lookup(entityName);
        if (entry == null) {
            unresolved.add(entityName);
            // No animation: fall back to a still image if the atlas happens to hold one under a
            // guessable id, otherwise an empty sprite that draws nothing but reports it.
            return new StaticEntitySprite(guessRegion(entityName));
        }
        try {
            // Parse + bake up front so the first frame this entity appears on does not stutter.
            player.loadSync(entry.path);
        } catch (RuntimeException e) {
            Gdx.app.error("SpriteRegistry", "could not load " + entry.path + " for " + entityName
                    + " -- falling back to a still image", e);
            unresolved.add(entityName);
            return new StaticEntitySprite(guessRegion(entityName));
        }
        // Part names are read once here rather than per draw: ArmorVisibility asks "does this zombie
        // have a cone part?" every frame for every zombie on the lawn.
        Set<String> parts = new LinkedHashSet<>();
        try {
            collectParts(player.getParts(entry.path), parts);
        } catch (RuntimeException e) {
            Gdx.app.log("SpriteRegistry", "no part list for " + entry.path + " (" + e + ")");
        }
        return new PamEntitySprite(player, entry.path, entry.clips, parts, entry.durations);
    }

    private AnimationEntry lookup(String entityName) {
        String override = NAME_OVERRIDES.get(entityName);
        if (override != null) {
            AnimationEntry entry = byName.get(override);
            if (entry != null) {
                return entry;
            }
            Gdx.app.error("SpriteRegistry", "override " + entityName + " -> " + override
                    + " points at an animation that does not exist");
        }
        AnimationEntry exact = byName.get(entityName);
        if (exact != null) {
            return exact;
        }
        return byNormalisedName.get(normalise(entityName));
    }

    // "Wall-nut" -> "WALLNUT", "Sun-shroom" -> "SUNSHROOM", "Primal Sunflower" -> "PRIMALSUNFLOWER".
    // The asset names use inconsistent separators (SNOWPEA but PRIMAL_SUNFLOWER), so dropping every
    // non-alphanumeric on both sides is what makes them line up.
    private static String normalise(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(Character.toUpperCase(c));
            }
        }
        return sb.toString();
    }

    // Last-ditch still image. RESOURCES.json ids look like IMAGE_PLANT_PEASHOOTER_PEASHOOTER_101X76 --
    // the trailing size makes them unguessable in general, so this only catches the easy shapes and is
    // expected to return null most of the time.
    private com.badlogic.gdx.graphics.g2d.TextureRegion guessRegion(String entityName) {
        String key = normalise(entityName);
        String[] candidates = {"IMAGE_PLANT_" + key, "IMAGE_ZOMBIE_" + key, "IMAGE_" + key, key};
        for (String candidate : candidates) {
            try {
                com.badlogic.gdx.graphics.g2d.TextureRegion region = bank.region(candidate);
                if (region != null) {
                    return region;
                }
            } catch (RuntimeException ignored) {
                // bank.region throws rather than returning null for some ids; treat both as "no match"
            }
        }
        return null;
    }

    // Everything that ended up on the still-image path. Log this once after warming the registry so
    // missing art is a visible, countable fact rather than a surprise mid-level.
    public Set<String> unresolvedNames() {
        return Collections.unmodifiableSet(unresolved);
    }

    public boolean hasAnimation(String entityName) {
        return lookup(entityName) != null;
    }

    // The animation's part tree, flattened. Parts are what a visibility map switches on and off, so
    // this is how you find out what an entity's cone, bucket or newspaper is actually called.
    // Diagnostic: -Dpvz.dumpParts=ZombieArmor1
    public java.util.List<String> partNames(String entityName) {
        AnimationEntry entry = lookup(entityName);
        if (entry == null) {
            return java.util.List.of();
        }
        try {
            player.loadSync(entry.path);
            java.util.List<String> names = new java.util.ArrayList<>();
            collectParts(player.getParts(entry.path), names);
            return names;
        } catch (RuntimeException e) {
            Gdx.app.error("SpriteRegistry", "could not read parts of " + entry.path, e);
            return java.util.List.of();
        }
    }

    private static void collectParts(PamPlayer.AnimationPart part, java.util.Collection<String> into) {
        if (part == null) {
            return;
        }
        if (part.name != null && !part.name.isBlank()) {
            into.add(part.name);
        }
        if (part.children != null) {
            for (PamPlayer.AnimationPart child : part.children) {
                collectParts(child, into);
            }
        }
    }

    // The clip names an entity actually defines -- useful when deciding whether to animate an action
    // or fall back to idle.
    public Set<String> clipsOf(String entityName) {
        AnimationEntry entry = lookup(entityName);
        return entry == null ? Set.of() : Collections.unmodifiableSet(entry.clips);
    }

    private record AnimationEntry(String name, String path, Set<String> clips,
                                  Map<String, Float> durations) { }
}
