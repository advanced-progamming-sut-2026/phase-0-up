package views.gdx.sprite;

import models.entities.plants.Plant;

import java.util.HashMap;
import java.util.Map;

// Works out how battered a defensive plant looks, and builds the libPVZ visibility map that shows it.
//
// Wall-nut's damaged looks are NOT separate clips. Its "damage" / "damage2" / "damage3" clips are
// blink-and-expression loops that every one of them draws a PRISTINE shell with; the cracked art lives
// in the part tree, as wallnut_body_front_damage_01..03, switched off until something asks for it.
// Selecting those clips alone -- which is all this renderer used to do -- changes the nut's face and
// nothing else, which is why its three "stages" were indistinguishable.
//
// So a stage is two things at once: the clip (the right expression, and the motion) and the part swap
// (the actual cracks). Both are driven from the one stage index computed here, so they cannot drift.
// The mechanism is the same one that puts a cone on a zombie -- see ArmorVisibility.
public final class PlantDamage {

    // Part swaps per plant: {the piece shown while unhurt, the stem its damaged variants share}.
    //
    // The stems are not derivable from the base name, so they are listed rather than guessed:
    // Wall-nut's mouth goes wallnut_mouth_closed -> wallnut_mouth_damage_01, dropping the "closed",
    // and Tall-nut's shell is tallnut_front, not tallnut_body_front. Add a plant here once its part
    // list has actually been dumped (-Dpvz.dumpParts=Tall-nut) -- guessing produces a silent no-op,
    // because a part name that does not exist simply never matches.
    private static final Map<String, String[][]> SWAPS = Map.of(
            "wall-nut", new String[][] {
                {"wallnut_body_front", "wallnut_body_front_damage_"},
                {"wallnut_mouth_closed", "wallnut_mouth_damage_"},
            },
            "tall-nut", new String[][] {
                {"tallnut_front", "tallnut_front_damage_"},
            });

    // Suffixes the artists used, in order. Wall-nut ships all three, Tall-nut only the first two.
    private static final String[] SUFFIXES = {"01", "02", "03"};

    // Plant+stage combinations already reported by -Dpvz.debugCounts, so the map is logged once rather
    // than sixty times a second.
    private static final java.util.Set<String> LOGGED = new java.util.HashSet<>();

    private PlantDamage() { }

    // Plants whose numbered IDLE clips are damage stages rather than blink variants.
    //
    // There is no way to tell the two apart from the art. Peashooter's idle2 is the same plant taking
    // another breath; Pumpkin's idle2 is the same shell with a piece out of it, and idle3 is one good
    // bite from gone. Cycling those -- which is what happens to every other plant's idles -- made a
    // brand-new Pumpkin flicker between intact and nearly broken twice a second.
    private static final java.util.Set<String> IDLE_IS_DAMAGE = java.util.Set.of("pumpkin");

    public static boolean idleIsDamage(String plantName) {
        return IDLE_IS_DAMAGE.contains(plantName == null ? "" : plantName.toLowerCase());
    }

    // How many damaged looks this plant actually has, counting clips, part swaps and staged idles, and
    // taking whichever offers most. A plant with damage clips but no part swaps still degrades, just by
    // expression alone -- which is what every plant did before the swaps existed.
    //
    // boosted matters for the staged-idle plants: a Pumpkin that has eaten plant food is a reinforced
    // Pumpkin and the dump draws FOUR states of it rather than three.
    public static int stageCount(EntitySprite sprite, String plantName, boolean boosted) {
        return Math.max(idleStages(sprite, plantName, boosted),
                Math.max(clipStages(sprite), partStages(sprite, plantName)));
    }

    // One fewer than the number of idle looks: the first is "unhurt".
    private static int idleStages(EntitySprite sprite, String plantName, boolean boosted) {
        if (!idleIsDamage(plantName)) {
            return 0;
        }
        return Math.max(0, PlantStages.idleVariants(sprite, 0, 1, boosted).size() - 1);
    }

    // 0 = unhurt, 1..stages = progressively more wrecked.
    //
    // Health is split into stages+1 equal bands, so a plant is unhurt for exactly as long as it wears
    // any one of its damaged looks. The old fixed 75/50/25 split wasted a stage on any plant that has
    // only two: Tall-nut spent its last two bands in the same pose.
    public static int stageFor(Plant plant, int stages) {
        if (stages <= 0) {
            return 0;
        }
        if (views.gdx.core.DebugFlags.FORCE_DAMAGE_STAGE >= 0) {
            return Math.min(views.gdx.core.DebugFlags.FORCE_DAMAGE_STAGE, stages);
        }
        if (plant.getHealth() == null) {
            return 0;
        }
        int max = Math.max(1, plant.getHealth().getMaxHp());
        float fraction = plant.getHealth().getCurrentHp() / (float) max;
        int stage = (int) Math.ceil((1f - fraction) * (stages + 1)) - 1;
        return Math.max(0, Math.min(stage, stages));
    }

    // The clip carrying this stage's expression, or null to stay on idle.
    public static String clipFor(EntitySprite sprite, int stage) {
        if (stage <= 0) {
            return null;
        }
        String clip = stage == 1 ? "damage" : "damage" + stage;
        return sprite.hasClip(clip) ? clip : null;
    }

    // The parts to swap for this stage, or null when there is nothing to switch -- which lets
    // PamEntitySprite take its cheaper no-map draw path.
    public static Map<String, Boolean> visibilityFor(EntitySprite sprite, String plantName, int stage) {
        String[][] swaps = SWAPS.get(plantName == null ? "" : plantName.toLowerCase());
        if (swaps == null || stage <= 0 || stage > SUFFIXES.length) {
            return null;
        }
        String suffix = SUFFIXES[stage - 1];

        Map<String, Boolean> visibility = null;
        for (String[] swap : swaps) {
            String damaged = swap[1] + suffix;
            if (!sprite.hasPart(damaged)) {
                continue;
            }
            if (visibility == null) {
                visibility = new HashMap<>();
            }
            // Hiding the intact piece matters as much as showing the cracked one: they occupy the same
            // place in the skeleton, so leaving the pristine shell on simply covers the damage up.
            visibility.put(swap[0], false);
            visibility.put(damaged, true);
        }
        if (views.gdx.core.DebugFlags.BOARD_COUNTS && LOGGED.add(plantName + stage)) {
            com.badlogic.gdx.Gdx.app.log("PlantDamage",
                    plantName + " stage " + stage + " -> " + visibility);
        }
        return visibility;
    }

    private static int clipStages(EntitySprite sprite) {
        int stages = 0;
        for (int i = 1; i <= SUFFIXES.length; i++) {
            if (!sprite.hasClip(i == 1 ? "damage" : "damage" + i)) {
                break;
            }
            stages++;
        }
        return stages;
    }

    private static int partStages(EntitySprite sprite, String plantName) {
        String[][] swaps = SWAPS.get(plantName == null ? "" : plantName.toLowerCase());
        if (swaps == null) {
            return 0;
        }
        int stages = 0;
        for (String suffix : SUFFIXES) {
            boolean present = false;
            for (String[] swap : swaps) {
                if (sprite.hasPart(swap[1] + suffix)) {
                    present = true;
                    break;
                }
            }
            if (!present) {
                break;
            }
            stages++;
        }
        return stages;
    }
}
