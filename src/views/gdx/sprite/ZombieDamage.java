package views.gdx.sprite;

import models.entities.zombies.Components.ArmorType;
import models.entities.zombies.Components.HealthLayer;
import models.entities.zombies.Zombie;

import java.util.IdentityHashMap;
import java.util.Map;

// A zombie falling apart as it is shot: the outer arm comes off and leaves the bone behind.
//
// This is the limb half of T8.5, and it works the same way the armor half already does -- the shared
// zombie body carries every piece as a separately hideable part, so "losing an arm" is switching four
// parts off and one on. `zombie_arm_outer_upper_bone` exists in the art for precisely this: it is the
// exposed humerus that shows once the flesh of the upper arm is hidden.
//
// The trigger is the BODY layer's health, not the total. A Buckethead with a full bucket and a chewed
// body should be missing its arm; one with a fresh body and a dented bucket should not, and total HP
// cannot tell those apart.
//
// How full is "full" is watched rather than asked, exactly as DamageFlash watches for a hit. The model
// exposes a layer's current HP but not its maximum, and `ArmorType.BASE_BODY.getHp()` is a default
// (190) rather than the zombie's own -- a Gargantuar's body is 3000. So the largest body HP this class
// has ever seen for a given zombie IS its maximum: a zombie is spawned at full health and is drawn from
// its first frame off the right-hand edge, so the first sighting is the full value. Watching also
// survives `scaleHp`, which a hypnotised zombie goes through and which would break any fixed reference.
public final class ZombieDamage {

    // The flesh of the outer arm, its forearm, and every hand pose the walk and eat cycles use. All
    // four go when the arm does; the bone underneath comes on.
    private static final String[] LOST_WITH_ARM = {
            "zombie_arm_outer_upper",
            "zombie_arm_outer_lower",
            "zombie_hand_outer_01",
            "zombie_hand_outer_02",
            "zombie_hand_outer_03",
    };

    public static final String ARM_BONE = "zombie_arm_outer_upper_bone";

    // Half the body's health. PvZ2 sheds the arm around the middle of a zombie's life, which is late
    // enough to mean something and early enough that the player sees it walk on afterwards -- a limb
    // that comes off at 10% is indistinguishable from the death that follows it a second later.
    private static final float ARM_THRESHOLD = 0.5f;

    // One call per zombie per frame answers both questions the renderer has -- how to draw it, and
    // whether this is the frame to throw the severed arm. Two separate methods would have to be called
    // in the right order to work, which is the kind of contract that breaks quietly.
    public enum ArmState {
        INTACT,
        // The frame it came off, and only that frame.
        JUST_LOST,
        LOST
    }

    private final Map<Zombie, Integer> fullBodyHp = new IdentityHashMap<>();
    private final Map<Zombie, Boolean> seenThisFrame = new IdentityHashMap<>();
    private final Map<Zombie, Boolean> announced = new IdentityHashMap<>();

    // Call once per zombie per frame.
    public ArmState armState(Zombie zombie) {
        if (zombie == null || zombie.getHealth() == null) {
            return ArmState.INTACT;
        }
        seenThisFrame.put(zombie, Boolean.TRUE);
        int current = bodyHp(zombie);
        if (current < 0) {
            return ArmState.INTACT;
        }
        int full = Math.max(current, fullBodyHp.getOrDefault(zombie, 0));
        fullBodyHp.put(zombie, full);
        if (full <= 0 || current / (float) full > ARM_THRESHOLD) {
            return ArmState.INTACT;
        }
        // Rising edge, so the arm is thrown once rather than every frame the zombie stays below the
        // threshold -- which for a Gargantuar is most of a minute.
        return announced.put(zombie, Boolean.TRUE) == null ? ArmState.JUST_LOST : ArmState.LOST;
    }

    // Adds the arm's parts to an existing visibility map, or builds one if there was nothing to toggle.
    //
    // Only ever called for a sprite that actually has the bone: the shared body has it, but a zombie
    // with its own animation (a Gargantuar, an Imp, a knight) generally does not, and naming a part that
    // is not there is a silent no-op that would leave the arm on with no way to tell.
    public static Map<String, Boolean> applyArmLoss(EntitySprite sprite,
                                                    Map<String, Boolean> visibility) {
        if (sprite == null || !sprite.hasPart(ARM_BONE)) {
            return visibility;
        }
        Map<String, Boolean> map = visibility == null ? new java.util.HashMap<>() : visibility;
        for (String part : LOST_WITH_ARM) {
            if (sprite.hasPart(part)) {
                map.put(part, false);
            }
        }
        map.put(ARM_BONE, true);
        return map;
    }

    // Whether this animation can show the loss at all. Used to decide whether throwing the severed arm
    // would leave a zombie that still visibly has both of them.
    public static boolean canLoseArm(EntitySprite sprite) {
        return sprite != null && sprite.hasPart(ARM_BONE);
    }

    // Drops zombies that were not drawn this frame, so a long level does not accumulate an entry for
    // every zombie that ever walked. Same contract as DamageFlash.sweep.
    public void sweep() {
        fullBodyHp.keySet().removeIf(zombie -> !seenThisFrame.containsKey(zombie));
        announced.keySet().removeIf(zombie -> !seenThisFrame.containsKey(zombie));
        seenThisFrame.clear();
    }

    // The bottom of the stack is the body -- HealthComponent puts a BASE_BODY layer there in its
    // constructor and armor is pushed on top. -1 when there is none, which should not happen but is
    // not worth throwing over in a renderer.
    private static int bodyHp(Zombie zombie) {
        for (HealthLayer layer : zombie.getHealth().getLayers()) {
            if (layer.getType() == ArmorType.BASE_BODY) {
                return layer.getCurrentHp();
            }
        }
        return -1;
    }
}
