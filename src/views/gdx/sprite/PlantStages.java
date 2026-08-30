package views.gdx.sprite;

import java.util.ArrayList;
import java.util.List;

// Which clip a plant should be playing, given that the dump names its resting pose four different ways.
//
// There is no house style in pvz-assets. A plant's idle is called any of:
//
//   idle, idle2                 the common case                      PEASHOOTER, ROTORUTABAGA
//   idle_stageN, idle2_stageN   one set per GROWTH stage             SUNSHROOM, PUFFSHROOM
//   idle_stageN_, idle_stageN_2 the same, with a trailing separator  KIWIBEAST
//   idleL_V                     one set per upgrade LEVEL, V variants CAULIPOWER, ELECTRICBLUEBERRY
//   stageN_idle, stageN_idle2   the stage as a PREFIX                DOOMSHROOM
//   loop                        intro / loop / outro                 the Empower-mints
//   (none at all)               only attack clips                    GRAVEBUSTER
//
// Asking for a literal "idle" therefore draws NOTHING for a good fraction of the roster -- Sun-shroom,
// Puff-shroom, Grave Buster, Doom-shroom and every mint were invisible on the lawn and blank in the
// almanac. Every lookup goes through here instead, and the last resort is the animation's own first
// clip, so a plant can be drawn wrong but never drawn not-at-all.
//
// Growth stage and level both come from the model (Plant.getGrowthStage, Plant.getLevel); this only
// decides what to call them.
public final class PlantStages {

    private static final String SUFFIX = "_stage";
    // Upgrades cap at 4, and no animation ships more idle variants than this per level.
    private static final int MAX_LEVEL = 4;
    private static final int MAX_VARIANT = 8;

    // Tried in order once every named scheme has missed. "loop" is the mints; the rest are plants whose
    // only clips are actions.
    private static final String[] LAST_RESORT = {"loop", "attack", "attack1", "animation", "walk"};

    private PlantStages() { }

    // The best clip this sprite has for one of these base names at this growth stage.
    //
    // Walks DOWN from the requested stage rather than only trying it: an upgrade can push a plant past
    // the number of stages its art was drawn for (Kiwibeast's "Max Size +1" appends a fourth), and
    // falling back to the top stage that exists beats falling through to a bare name the animation
    // does not have. Returns null when nothing matches, so callers can tell "no art for this action"
    // from "here is the idle instead".
    public static String clip(EntitySprite sprite, int stage, String... bases) {
        if (sprite == null || bases == null) {
            return null;
        }
        for (String base : bases) {
            String found = forBase(sprite, base, stage);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static String forBase(EntitySprite sprite, String base, int stage) {
        if (base == null) {
            return null;
        }
        for (int s = Math.max(stage + 1, 1); s >= 1; s--) {
            String staged = base + SUFFIX + s;
            if (sprite.hasClip(staged)) {
                return staged;
            }
            // Kiwibeast writes its idles as "idle_stage1_", "idle_stage1_2", "idle_stage1_3" -- the
            // first variant keeps the separator the others need for their number.
            if (sprite.hasClip(staged + "_")) {
                return staged + "_";
            }
            // Doom-shroom puts the stage in FRONT: stage1_idle, stage2_explode, stage3_idle2.
            String prefixed = "stage" + s + "_" + base;
            if (sprite.hasClip(prefixed)) {
                return prefixed;
            }
        }
        return sprite.hasClip(base) ? base : null;
    }

    // The clip for a plant that has been STACKED this many deep, or null if there is no such clip.
    //
    // Pea Pod is the only one of these: it is a single plant carrying up to five heads, and the dump
    // draws all five -- "idle" through "idle5", "attack" through "attack 5". The head count, not a
    // cycle, is what chooses between them, which is why this is separate from idleVariants: a one-headed
    // pod alternating onto "idle2" is showing the player a head that is not there.
    //
    // Both spellings are tried because the dump uses both in the SAME animation: its idles run the
    // number straight on and its attacks separate it with a space.
    public static String stacked(EntitySprite sprite, String base, int heads) {
        if (sprite == null || base == null || heads <= 1) {
            return null;
        }
        for (String candidate : new String[] {base + heads, base + " " + heads, base + "_" + heads}) {
            if (sprite.hasClip(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    // The clip a plant plays while it acts: a swing, a shot, a bloom.
    //
    // `variant` is the form of the action the model chose (Kernel-pult lobs a kernel or, on a roll,
    // stunning butter, and the art has a separate swing for each); `heads` is how many a stacking plant
    // is carrying, which takes priority -- "attack 3" is a Pea Pod with three heads firing, and no
    // ordinary shooter has such a clip to be caught by this.
    //
    // "bite" before "special": Chomper's action clip is `bite` and it has no `attack` at all, while its
    // `special` is the two-second gulp it plays when it swallows something whole. Falling through to
    // `special` made every ordinary chomp look like the swallow. Nothing that uses `special` as its
    // action (Sunflower's bloom) has a `bite`, so this is safe.
    public static String actionClip(EntitySprite sprite, int stage, int variant, int heads,
                                    boolean boosted) {
        String stacked = stacked(sprite, "attack", heads);
        if (stacked != null) {
            return stacked;
        }
        String upgraded = boosted ? boosted(sprite, "attack") : null;
        if (upgraded != null) {
            return upgraded;
        }
        // "special" is numbered too, for a plant whose action IS its special: Bowling Bulb rolls a cyan,
        // a blue or an orange bulb and the art is `special`, `special2`, `special3`. It has no `attack`
        // of any kind, so asking only for a numbered attack found nothing and it stood still.
        return variant > 0
                ? clip(sprite, stage, "attack" + (variant + 1), "special" + (variant + 1),
                        "attack", "shooting", "special")
                : clip(sprite, stage, "attack", "shooting", "bite", "special");
    }

    // The permanently-upgraded twin of a clip, for a plant the dump draws in two states for good.
    //
    // Cactus is the one: its plant food is not a burst, it is an upgrade it keeps for the rest of the
    // level, and the art ships a whole second set for afterwards -- idle_plantfood, idle_plantfood2,
    // idle_plantfood3, attack_plantfood. A boosted Cactus was drawn in its unboosted pose, so the one
    // visible sign that the upgrade had happened was that its spikes went through everything.
    public static String boosted(EntitySprite sprite, String base) {
        if (sprite == null || base == null) {
            return null;
        }
        String name = base + BOOST_SUFFIX;
        return sprite.hasClip(name) ? name : null;
    }

    private static final String BOOST_SUFFIX = "_plantfood";

    // Every resting clip this plant has right now, in the order they should be cycled.
    //
    // A list rather than one name because most of these plants ship two or more idle variants and the
    // real game alternates them -- it is what makes a plant blink rather than sway forever on one loop.
    // Never empty: the last entry is whatever the animation's first clip happens to be.
    public static List<String> idleVariants(EntitySprite sprite, int growthStage, int level) {
        return idleVariants(sprite, growthStage, level, false);
    }

    // boosted asks for the permanently-upgraded set first, where the art has one. See boosted().
    public static List<String> idleVariants(EntitySprite sprite, int growthStage, int level,
                                            boolean boosted) {
        List<String> found = new ArrayList<>();
        if (sprite == null) {
            return List.of(ClipMap.IDLE);
        }
        if (boosted) {
            addBoosted(sprite, found);
        }
        if (found.isEmpty()) {
            addStaged(sprite, growthStage, found);
        }
        if (found.isEmpty()) {
            addLevelled(sprite, level, found);
        }
        if (found.isEmpty()) {
            addPlain(sprite, found);
        }
        if (found.isEmpty()) {
            addIdlePrefixed(sprite, found);
        }
        if (found.isEmpty()) {
            addLastResort(sprite, found);
        }
        return found.isEmpty() ? List.of(ClipMap.IDLE) : found;
    }

    // idle_plantfood, idle_plantfood2, idle_plantfood3 -- the resting set of a permanently upgraded
    // plant, cycled like any other. Stops at the first gap, so a plant with none adds nothing.
    private static void addBoosted(EntitySprite sprite, List<String> into) {
        for (int v = 1; v <= MAX_VARIANT; v++) {
            String candidate = ClipMap.IDLE + BOOST_SUFFIX + (v == 1 ? "" : String.valueOf(v));
            if (!sprite.hasClip(candidate)) {
                return;
            }
            into.add(candidate);
        }
    }

    // idle_stageN / idle2_stageN, and Doom-shroom's stageN_idle / stageN_idle2.
    private static void addStaged(EntitySprite sprite, int growthStage, List<String> into) {
        for (String base : new String[] {ClipMap.IDLE, "idle2"}) {
            String staged = forStageOnly(sprite, base, growthStage);
            if (staged != null) {
                into.add(staged);
            }
        }
    }

    // Deliberately NOT forBase: that falls back to the bare name, which would put a plain "idle" in the
    // staged list and stop addPlain from ever running for a plant that has both.
    private static String forStageOnly(EntitySprite sprite, String base, int stage) {
        for (int s = Math.max(stage + 1, 1); s >= 1; s--) {
            for (String candidate : new String[] {
                base + SUFFIX + s, base + SUFFIX + s + "_", "stage" + s + "_" + base}) {
                if (sprite.hasClip(candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    // idleL_V -- Caulipower and Electric Blueberry are drawn once per UPGRADE level, with a handful of
    // variants each. Walks the level down so a level-4 plant whose art stops at 3 still finds a pose.
    private static void addLevelled(EntitySprite sprite, int level, List<String> into) {
        for (int l = Math.min(Math.max(level, 1), MAX_LEVEL); l >= 1; l--) {
            for (int v = 1; v <= MAX_VARIANT; v++) {
                String candidate = ClipMap.IDLE + l + "_" + v;
                if (!sprite.hasClip(candidate)) {
                    break;
                }
                into.add(candidate);
            }
            if (!into.isEmpty()) {
                return;
            }
        }
    }

    private static void addPlain(EntitySprite sprite, List<String> into) {
        for (String base : new String[] {ClipMap.IDLE, "idle2"}) {
            if (sprite.hasClip(base)) {
                into.add(base);
            }
        }
    }

    // An idle named after the prop the entity is holding: "idle_newspaper", and any other entity whose
    // resting pose carries a suffix none of the schemes above predict.
    //
    // This step exists because the LAST_RESORT list below contains "walk", and Newspaper Zombie has
    // "walk" but no bare "idle" -- so it fell straight past every scheme and rested on its walk cycle.
    // That is not merely the wrong pose: bounds() unions every frame of a clip, and a walk cycle sweeps
    // the whole stride, so the box came back 808x378 against idle_newspaper's 175x204. Everything that
    // scales art to fit a box -- a seed card, an almanac tile, the live view -- then drew the zombie at
    // well under half the size of its neighbours. Anything literally named idle-something beats a guess.
    private static void addIdlePrefixed(EntitySprite sprite, List<String> into) {
        for (String clip : sprite.clips()) {
            if (clip != null && clip.startsWith(ClipMap.IDLE)) {
                into.add(clip);
            }
        }
    }

    // Nothing idle-shaped exists. Grave Buster has only attack clips and the mints only intro/loop/
    // outro, so a named guess comes first and the animation's own first clip catches the rest.
    private static void addLastResort(EntitySprite sprite, List<String> into) {
        for (String candidate : LAST_RESORT) {
            if (sprite.hasClip(candidate)) {
                into.add(candidate);
                return;
            }
        }
        for (String any : sprite.clips()) {
            into.add(any);
            return;
        }
    }

    // The transition INTO a stage: the clip played once as the plant grows from stage-1 to stage.
    //
    // Indexed by the stage being LEFT, one-based -- growing from model stage 0 to 1 plays
    // "growth_stage1". The last stage has no growth clip, which is correct: nothing grows out of it.
    public static String growthClip(EntitySprite sprite, int fromStage) {
        if (sprite == null) {
            return null;
        }
        String name = "growth" + SUFFIX + (fromStage + 1);
        if (sprite.hasClip(name)) {
            return name;
        }
        return sprite.hasClip(name + "_") ? name + "_" : null;
    }

    // The resting pose for a plant shown OUTSIDE the lawn -- a seed card, an almanac page, the ghost
    // that follows the cursor while placing one.
    //
    // First stage and first level: none of those places has a grown or upgraded plant to describe, and
    // a seed packet showing a fully grown Sun-shroom would promise something planting does not give.
    public static String restingClip(EntitySprite sprite) {
        return idleVariants(sprite, 0, 1).get(0);
    }
}
