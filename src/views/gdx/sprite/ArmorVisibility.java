package views.gdx.sprite;

import models.entities.zombies.Components.ArmorType;
import models.entities.zombies.Components.HealthLayer;
import models.entities.zombies.Zombie;

import java.util.HashMap;
import java.util.Map;

// Turns a zombie's HealthComponent layer stack into a libPVZ visibility map.
//
// PvZ2 does not ship a separate animation for a conehead: the base zombie's PAM carries every armor
// piece as a hideable part, and the game switches them on. Our model already matches that shape --
// armor is a stack of HealthLayers over a BASE_BODY -- so the translation is direct, and armor
// disappearing when destroyed needs no extra bookkeeping at all: the layer is simply gone from the
// stack and stops being named here.
//
// The parts also come in three damage stages (_norm, _damage_01, _damage_02), so the "armor visually
// cracks as it loses health" aesthetic falls out of the same mapping for free -- pick the stage from
// the layer's remaining HP.
public final class ArmorVisibility {

    // Part-name stems, keyed by the armor our model defines. Types with no stem (a barrel, an ice
    // block, a knight's pauldron) live on their own zombie's animation rather than the shared body,
    // and are added here as each is verified against that animation's part list.
    private static final Map<ArmorType, String> STEMS = new HashMap<>();

    static {
        STEMS.put(ArmorType.CONE, "zombie_armor_cone");
        STEMS.put(ArmorType.BUCKET, "zombie_armor_bucket");
        STEMS.put(ArmorType.BRICK, "zombie_armor_brick");
    }

    // Status-effect overlays that exist on essentially every zombie body.
    private static final String BUTTER_PART = "butter";

    private ArmorVisibility() { }

    // Builds the visibility map for one zombie this frame. Returns null when there is nothing to
    // toggle, which lets PamEntitySprite take the cheaper no-map draw path.
    public static Map<String, Boolean> forZombie(Zombie zombie, EntitySprite sprite) {
        Map<String, Boolean> visibility = null;

        for (HealthLayer layer : zombie.getHealth().getLayers()) {
            ArmorType type = layer.getType();
            String stem = type == null ? null : STEMS.get(type);
            if (stem == null) {
                continue;
            }
            String part = stem + "_" + damageStage(layer, type);
            if (!sprite.hasPart(part)) {
                continue;
            }
            if (visibility == null) {
                visibility = new HashMap<>();
            }
            visibility.put(part, true);
        }

        // Buttered zombies are stunned and, in the original, wear a pat of butter on the head. The
        // spec only asks that the status be visually distinct; this is better than a colour shift.
        if (zombie.getState().isButtered() && sprite.hasPart(BUTTER_PART)) {
            if (visibility == null) {
                visibility = new HashMap<>();
            }
            visibility.put(BUTTER_PART, true);
        }

        return visibility;
    }

    // _norm above two thirds, _damage_01 above one third, _damage_02 below.
    //
    // Measured against ArmorType.getHp() -- the type's full-health value -- rather than the layer's
    // own maxHp, because a hypnotised zombie's layers get scaled and would otherwise never look
    // damaged.
    private static String damageStage(HealthLayer layer, ArmorType type) {
        int max = Math.max(1, type.getHp());
        float fraction = layer.getCurrentHp() / (float) max;
        if (fraction > 0.66f) {
            return "norm";
        }
        if (fraction > 0.33f) {
            return "damage_01";
        }
        return "damage_02";
    }
}
