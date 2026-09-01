package views.gdx.sprite;

import models.entities.zombies.Components.ArmorType;
import models.entities.zombies.Components.HealthLayer;
import models.entities.zombies.Zombie;

import java.util.HashMap;
import java.util.List;
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

    // No stems for CROWN or SHOULDER_ARMOR, and that is deliberate rather than an omission.
    //
    // They are the knight's two pieces, and no body a zombie wearing them is ever drawn from carries a
    // part for either: the shared ZOMBIE_TUTORIAL has only the cone, the bucket and the brick. A zombie
    // that earns a knighthood mid-lane is therefore drawn as the Knight Zombie outright rather than
    // having armour switched on -- see ZombieRenderer.KNIGHT_ALIAS.

    // Status-effect overlays that exist on essentially every zombie body.
    private static final String BUTTER_PART = "butter";

    private ArmorVisibility() { }

    // The undamaged stage, for a specimen nobody has shot at yet.
    private static final String PRISTINE = "norm";

    // The armor a zombie TYPE wears, with no live zombie to ask.
    //
    // The almanac lists templates, not zombies -- and Conehead, Buckethead and Brick are all the SAME
    // animation: one body with a different hat switched on. ZombieArmor1, ZombieArmor2 and ZombieArmor4
    // resolve to one PAM whose part list carries all three hats, so without a visibility map the three
    // of them draw as an identical bare zombie, which is exactly what the almanac did.
    //
    // Always the pristine stage: an almanac page describes the zombie you are about to meet, not one
    // that has already been chewed on.
    public static Map<String, Boolean> forArmorTypes(List<ArmorType> armors, EntitySprite sprite) {
        if (armors == null || sprite == null) {
            return null;
        }
        Map<String, Boolean> visibility = null;
        for (ArmorType type : armors) {
            String part = partName(type, PRISTINE);
            if (part == null || !sprite.hasPart(part)) {
                continue;
            }
            if (visibility == null) {
                visibility = new HashMap<>();
            }
            visibility.put(part, true);
        }
        return visibility;
    }

    // The same thing again, starting from a registry alias instead of a template.
    //
    // Every place that draws a zombie NOT on the lawn needs this -- the almanac's tiles and detail page,
    // I-Zombie's roster bar, the cursor ghost of a zombie about to be summoned -- and all of them have
    // an alias rather than a template in hand. It lives here rather than being looked up per screen
    // because the failure is silent: forget it and the zombie simply comes out bare-headed.
    public static Map<String, Boolean> forAlias(String alias, EntitySprite sprite) {
        if (alias == null || sprite == null) {
            return null;
        }
        models.templates.ZombieTemplate template =
                utils.registry.ZombieRegistry.getInstance().getZombieTemplateByAlias(alias);
        return template == null ? null : forArmorTypes(template.getArmors(), sprite);
    }

    // Builds the visibility map for one zombie this frame. Returns null when there is nothing to
    // toggle, which lets PamEntitySprite take the cheaper no-map draw path.
    public static Map<String, Boolean> forZombie(Zombie zombie, EntitySprite sprite) {
        Map<String, Boolean> visibility = null;

        for (HealthLayer layer : zombie.getHealth().getLayers()) {
            ArmorType type = layer.getType();
            String part = type == null ? null : partName(type, damageStage(layer, type));
            if (part == null || !sprite.hasPart(part)) {
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

    // Every damage stage of every armor stem, so the whole family can be named at once.
    private static final String[] STAGES = {PRISTINE, "damage_01", "damage_02"};

    // The parts of this sprite that are switched off unless something switches them on -- every armor
    // piece it is not wearing, plus the status overlays.
    //
    // This is what a card has to leave out of its measurements. bounds() unions every part whether drawn
    // or not, so a Conehead measured naively carries the brick helmet it has not got: a hat's worth of
    // empty space above its head, and the zombie itself drawn small to make room for it.
    //
    // Callers subtract whatever they are actually showing -- see EntityIcon.
    public static java.util.Set<String> togglePartsOf(EntitySprite sprite) {
        java.util.Set<String> optional = new java.util.LinkedHashSet<>();
        if (sprite == null) {
            return optional;
        }
        for (String stem : STEMS.values()) {
            for (String stage : STAGES) {
                String part = stem + "_" + stage;
                if (sprite.hasPart(part)) {
                    optional.add(part);
                }
            }
        }
        if (sprite.hasPart(BUTTER_PART)) {
            optional.add(BUTTER_PART);
        }
        // The squash-splat overlay, on the shared body alongside the butter.
        if (sprite.hasPart("ink")) {
            optional.add("ink");
        }
        return optional;
    }

    // What to leave out when measuring an entity that is being drawn with `shown`.
    //
    // Everything toggleable except the pieces actually switched on -- so a Conehead is measured with its
    // cone but without the bucket and brick, and a bare zombie without any of the three. Pass this to
    // EntitySprite.visibleBounds.
    public static java.util.Set<String> hiddenParts(EntitySprite sprite,
                                                    Map<String, Boolean> shown) {
        java.util.Set<String> hidden = togglePartsOf(sprite);
        if (shown != null) {
            for (Map.Entry<String, Boolean> entry : shown.entrySet()) {
                if (Boolean.TRUE.equals(entry.getValue())) {
                    hidden.remove(entry.getKey());
                }
            }
        }
        return hidden;
    }

    // The part name for a piece of armor that has just been destroyed, for the fly-off effect.
    //
    // Always the most damaged stage, and that is not an approximation: armor is destroyed by being
    // whittled through every stage in turn, so the piece that comes loose was in `damage_02` on the
    // frame before it went. Null for an armor with no part on the shared body -- a barrel, an ice
    // block, a knight's pauldron -- which correctly means "nothing to throw".
    public static String destroyedPartName(ArmorType type) {
        return partName(type, "damage_02");
    }

    // "zombie_armor_cone" + "norm" -> "zombie_armor_cone_norm". Null for an armor that lives on its own
    // zombie's animation rather than the shared body (a barrel, an ice block, a knight's pauldron).
    private static String partName(ArmorType type, String stage) {
        String stem = STEMS.get(type);
        return stem == null ? null : stem + "_" + stage;
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
