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

    // A second key, "GROUP/NAME", for names that exist under more than one group and whose right answer
    // depends on who is asking. See lookup.
    private final Map<String, AnimationEntry> byGroupedName = new HashMap<>();

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
    //  * A few plants (Cat-tail, Iceberg Lettuce) are simply not in this asset dump. They resolve to a
    //    still image and are listed at startup. Rotobaga, Kernel-pult and Phat Beet ARE in it under
    //    names normalisation cannot reach (ROTORUTABAGA, KERNALPULT, PHATBEETS) and are mapped below.
    private static final Map<String, String> NAME_OVERRIDES = buildOverrides();

    private static Map<String, String> buildOverrides() {
        Map<String, String> m = new LinkedHashMap<>();

        // --- zombies -------------------------------------------------------------------------------
        m.put("ZombieDefault", "ZOMBIE_TUTORIAL");
        m.put("ZombieArmor1", "ZOMBIE_TUTORIAL");        // cone   -> base body + armor part
        m.put("ZombieArmor2", "ZOMBIE_TUTORIAL");        // bucket -> base body + armor part
        m.put("ZombieArmor4", "ZOMBIE_TUTORIAL");        // helmet -> base body + armor part
        // ZombieDarkArmor3 is NOT here any more: it used to borrow ZOMBIE_DARK_KING's body, which is a
        // different zombie wearing a crown. It has no animation of its own in the dump, so it is served
        // a still instead -- see STILL_IMAGES.
        // The Dark Ages pair, chosen over the plain GARGANTUAR/GARGANTUAR_IMP for the clips they carry.
        // DARK_GARGANTUAR has fire + cannon_fire, which together are the imp throw; ZOMBIE_DARK_IMP_MONK
        // has fly + land, which is the imp ARRIVING. The default pair can only stand there. See
        // views.gdx.render.ZombieActions, which is what plays them.
        m.put("ZombieGargantuar", "DARK_GARGANTUAR");
        m.put("ZombieImp", "ZOMBIE_DARK_IMP_MONK");
        // The two adopted zombies whose animation name does not survive normalisation. The other four
        // (Prospector, Jane, Piano, All-Star) match on shape alone and need no entry.
        //   ZombieArcade      -> ZOMBIE80SARCADE          (the "80S" is not in the alias)
        //   ZombieCrystalSkull-> ZOMBIELOSTCITYCRYSTALSKULL (the art keeps its home world in the name)
        m.put("ZombieArcade", "ZOMBIE_80S_ARCADE");
        m.put("ZombieCrystalSkull", "ZOMBIE_LOSTCITY_CRYSTALSKULL");
        m.put("ZombieRa", "ZOMBIE_EGYPT_RA");
        m.put("ZombieTombRaiser", "ZOMBIE_EGYPT_TOMBRAISER");
        m.put("ZombieIceAgeDodo", "ZOMBIE_ICEAGE_DODORIDER");
        m.put("ZombieBeachSnorkel", "ZOMBIE_BEACH_SNORKELER");
        m.put("ZombieDarkJuggler", "ZOMBIE_DARK_JESTER");   // "juggler" in our data, Jester in the art
        m.put("ZombieWizard", "ZOMBIE_DARK_WIZARD");
        m.put("ZombieCrystalSkull", "ZOMBIE_LOSTCITY_CRYSTALSKULL");
        m.put("ZombieNewspaper", "ZOMBIE_MODERN_NEWSPAPER");
        m.put("ZombieArcade", "ZOMBIE_80S_ARCADE");
        // The four Zombotany plant-zombies, and the one group here that is a COMPOSITION rather than a
        // rename. The dump has no Zombotany art at all -- "botany" appears nowhere in animations.json or
        // RESOURCES.json -- so before this they resolved to nothing and drew nothing, not even a still.
        // They borrow the shared body, exactly as the armored zombies do, and views.gdx.render
        // .ZombotanyHead switches the skull off and stands the plant's own animation on the neck.
        m.put(factories.zombie.ZombotanyRoster.PEASHOOTER, "ZOMBIE_TUTORIAL");
        m.put(factories.zombie.ZombotanyRoster.WALLNUT, "ZOMBIE_TUTORIAL");
        m.put(factories.zombie.ZombotanyRoster.JALAPENO, "ZOMBIE_TUTORIAL");
        m.put(factories.zombie.ZombotanyRoster.SQUASH, "ZOMBIE_TUTORIAL");

        // --- plants --------------------------------------------------------------------------------
        m.put("Twin Sunflower", "SUNFLOWER_TWIN");
        m.put("Mega Gatling Pea", "MEGAGATLING");
        m.put("Phat Beet", "PHATBEETS");
        // The dump spells these two differently from our data: the vegetable is a rutabaga, and the
        // kernel is an "a".
        m.put("Rotobaga", "ROTORUTABAGA");
        m.put("Kernel-pult", "KERNALPULT");

        // The two mints this project invented.
        //
        // Seven of our nine Empower-mints match the dump by name alone (Appease/Arma/Bombard/Enchant/
        // Enforce/Enlighten/Reinforce). "Pierce-mint" and "catTail-mint" are ours, not PopCap's, so
        // there is no art with those names -- these two are a CHOICE, not a fact. Spear-mint is the
        // dump's piercing-shot mint, which is what Pierce-mint boosts (STRIKE_THROUGH); Fila-mint is
        // simply another unused one. Swap either line if a different mint reads better.
        m.put("Pierce-mint", "SPEARMINT");
        m.put("catTail-mint", "FILAMINT");
        return m;
    }

    // How much bigger the Knight Zombie's still has to be drawn to stand among animated zombies.
    //
    // Its almanac thumbnail is 66x115 at the 768 asset size, while a basic zombie's drawn body measures
    // about 131x197 -- so the Knight walked the lawn at a little over half everyone else's height. 197/115
    // is 1.71; rounded to 1.7, which puts him level with a Browncoat, and a hair taller after the extra
    // width, which suits a zombie in armour.
    //
    // This is a fudge factor and it is one on purpose: the honest fix is an animation, and there is none
    // in the dump. Nothing else needs it, because every other entity on the lawn is drawn from art that
    // was authored at lawn scale.
    private static final float KNIGHT_STILL_SCALE = 1.7f;

    // Entities the dump has a PICTURE of but no animation for.
    //
    // The still-image path already exists as a last resort (see StaticEntitySprite), but it can only
    // find a region whose id is guessable from the entity's name, which these two are not. Naming them
    // turns "draws nothing, or borrows the wrong zombie's body" into "draws itself, standing still".
    //
    // This wins over NAME_OVERRIDES on purpose: a named still is a deliberate answer, and borrowing
    // another entity's animation to avoid a blank is worse than not moving.
    //
    // The float is how much to enlarge the image by, and it matters because these are UI ART, drawn at
    // whatever size the UI needed. An animation is authored at lawn scale; an almanac thumbnail is not,
    // and standing one on the lawn unscaled draws an entity that is simply the wrong size next to its
    // neighbours -- which is what the Knight Zombie did. See KNIGHT_STILL_SCALE.
    private static final Map<String, Still> STILL_IMAGES = Map.of(
            // A knight, not the crowned king it used to borrow. No animation ships for it.
            "ZombieDarkArmor3",
            new Still("IMAGE_UI_ALMANAC_PACKETS_ZOMBIES_DARK_ARMOR3", KNIGHT_STILL_SCALE),
            // One of the handful of plants absent from the dump entirely; its seed packet is not.
            "Iceberg Lettuce", new Still("IMAGE_UI_PACKETS_ICEBURG", 1f));

    private record Still(String regionId, float scale) { }

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
            String group = groupOf(path);
            if (group != null) {
                byGroupedName.putIfAbsent(group + "/" + name, entry);
            }
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
        Still still = STILL_IMAGES.get(entityName);
        if (still != null) {
            com.badlogic.gdx.graphics.g2d.TextureRegion region = regionOrNull(still.regionId());
            if (region != null) {
                return new StaticEntitySprite(region, still.scale());
            }
            Gdx.app.error("SpriteRegistry", "still image " + still.regionId() + " for " + entityName
                    + " is not in the atlas");
        }
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
        Set<String> drawable = new LinkedHashSet<>();
        Map<String, Set<String>> descendants = new HashMap<>();
        try {
            PamPlayer.AnimationPart tree = player.getParts(entry.path);
            collectParts(tree, parts);
            collectDrawable(tree, drawable);
            collectDescendants(tree, descendants);
        } catch (RuntimeException e) {
            Gdx.app.log("SpriteRegistry", "no part list for " + entry.path + " (" + e + ")");
        }
        return new PamEntitySprite(player, entry.path, entry.clips, parts, drawable, descendants,
                entry.durations);
    }

    // The parts that actually carry an image, as opposed to the groups that hold them.
    //
    // A part tree has three kinds of node: ancestors like "root", groups like "zombie_armor_brick_norm"
    // whose children are themselves groups, and nodes with a resource child, which are the things drawn.
    // Only the last kind may be unioned to measure an extent -- ask libPVZ for "root" and it hands back
    // the whole animation's box, which is how a first attempt at excluding a zombie's hats came back with
    // the hats still in it.
    private static void collectDrawable(PamPlayer.AnimationPart part,
                                        java.util.Collection<String> into) {
        if (part == null) {
            return;
        }
        if (part.name != null && !part.name.isBlank() && !part.resource && hasResourceChild(part)) {
            into.add(part.name);
        }
        if (part.children != null) {
            for (PamPlayer.AnimationPart child : part.children) {
                collectDrawable(child, into);
            }
        }
    }

    private static boolean hasResourceChild(PamPlayer.AnimationPart part) {
        if (part.children == null) {
            return false;
        }
        for (PamPlayer.AnimationPart child : part.children) {
            if (child.resource) {
                return true;
            }
        }
        return false;
    }

    // Every name under each name, itself included. Hiding a group has to hide what it contains: a brick
    // helmet is "zombie_armor_brick_norm" holding a brick, two cement highlights and a trowel, so naming
    // only the group leaves four drawable pieces of it still being measured.
    private static Set<String> collectDescendants(PamPlayer.AnimationPart part,
                                                  Map<String, Set<String>> into) {
        Set<String> mine = new LinkedHashSet<>();
        if (part == null) {
            return mine;
        }
        if (part.name != null && !part.name.isBlank()) {
            mine.add(part.name);
        }
        if (part.children != null) {
            for (PamPlayer.AnimationPart child : part.children) {
                mine.addAll(collectDescendants(child, into));
            }
        }
        if (part.name != null && !part.name.isBlank()) {
            // merge rather than overwrite: a name can appear in more than one branch (the armor pieces are
            // listed again under "_particles"), and both branches' children belong to it.
            into.computeIfAbsent(part.name, key -> new LinkedHashSet<>()).addAll(mine);
        }
        return mine;
    }

    // "768/FULL/VASEBREAKER/VASE_GARGANTUAR/VASE_GARGANTUAR.PAM" -> "VASEBREAKER".
    //
    // The folder one level above the animation's own is what the game files the asset under, which is
    // exactly the distinction pathScore is guessing at from a handful of known group names.
    private static String groupOf(String path) {
        String[] parts = path.split("/");
        return parts.length < 3 ? null : parts[parts.length - 3].toUpperCase(java.util.Locale.ROOT);
    }

    private AnimationEntry lookup(String entityName) {
        // "GROUP/NAME" asks for one specific asset, for the cases where the shared name is genuinely
        // ambiguous and pathScore's heuristic cannot help because BOTH answers are gameplay actors.
        //
        // VASE_GARGANTUAR is the one that forced this: VASEBREAKER/VASE_GARGANTUAR is the vase, and
        // ZOMBIE/VASE_GARGANTUAR is the Gargantuar that climbs out of it. pathScore awards /ZOMBIE/ a
        // hundred points, so asking for the bare name drew a two-tile Gargantuar standing on the board
        // in place of every special vase -- correct art, wrong asset, and nothing logged.
        if (entityName.indexOf('/') >= 0) {
            AnimationEntry grouped = byGroupedName.get(entityName.toUpperCase(java.util.Locale.ROOT));
            if (grouped != null) {
                return grouped;
            }
            Gdx.app.error("SpriteRegistry", "no animation " + entityName
                    + " -- falling back to the unqualified name");
            return lookup(entityName.substring(entityName.indexOf('/') + 1));
        }
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
            com.badlogic.gdx.graphics.g2d.TextureRegion region = regionOrNull(candidate);
            if (region != null) {
                return region;
            }
        }
        return null;
    }

    // bank.region throws rather than returning null for some ids; both mean "no match" here.
    private com.badlogic.gdx.graphics.g2d.TextureRegion regionOrNull(String id) {
        try {
            return bank.region(id);
        } catch (RuntimeException missing) {
            return null;
        }
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
