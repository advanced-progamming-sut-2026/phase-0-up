package factories.plant;

import models.entities.plants.abilities.ShootDirection;
import models.entities.plants.abilities.triggers.AlwaysTrueTrigger;
import models.entities.plants.abilities.triggers.BackwardStandardTrigger;
import models.entities.plants.abilities.triggers.ContactTrigger;
import models.entities.plants.abilities.triggers.ForwardShortRangeTrigger;
import models.entities.plants.abilities.triggers.ForwardStandardTrigger;
import models.entities.plants.abilities.triggers.GlobalTrigger;
import models.entities.plants.abilities.triggers.MultiLaneTrigger;
import models.entities.plants.abilities.triggers.TriggerStrategy;
import models.entities.projectiles.Trajectory;
import models.templates.PlantTemplate.AbilityParams;

// Chooses the firing condition for a plant's ability, matching the real game's behaviour.
// Kept separate from PlantAbilityFactory so the "when does it fire" policy lives in one place.
public final class TriggerResolver {
    private TriggerResolver() { }

    // Whether this ability's shots can hurt a grave, and therefore whether the plant should open fire
    // on one with no zombie in sight. This is the single gate for the whole grave-targeting feature.
    //
    // The test is the projectile's trajectory, because that is what physically decides the outcome: a
    // DIRECT shot travels along the ground, collides with a grave and chips it down, so a lane walled
    // off by graves is a problem that plant can actually solve. A LOBBED shot (the -pult family) arcs
    // over terrain by design and passes straight over a grave without touching it -- letting one fire
    // at a grave would just burn its cooldown on a shot that can never land. Trajectory is also how
    // Projectile.handleTerrainCollisions decides the very same thing, so the two stay in agreement.
    private static boolean targetsGraves(AbilityParams params) {
        return params == null || params.getTrajectory() == null
                || params.getTrajectory() == Trajectory.DIRECT;
    }

    // Straight-ahead shooters: peashooters, snow pea, cactus, catapults, etc. Short-range shooters
    // (Puff/Sea/Fume-shroom, which carry a maxRange) only fire once a target is within reach.
    public static TriggerStrategy forShooter(AbilityParams params) {
        if (params != null && params.getMaxRange() > 0.0) {
            return new ForwardShortRangeTrigger(params.getMaxRange(), targetsGraves(params));
        }
        return new ForwardStandardTrigger(targetsGraves(params));
    }

    // Split Pea's rear volley and any other backward-facing shot.
    public static TriggerStrategy forDirection(AbilityParams params) {
        if (params != null && params.getDirection() == ShootDirection.BACKWARD) {
            return new BackwardStandardTrigger(targetsGraves(params));
        }
        return forShooter(params);
    }

    // Threepeater and friends: fire when a zombie stands in any of the covered lanes.
    public static TriggerStrategy forMultiLane(AbilityParams params) {
        int[] offsets = (params != null && params.getRowOffsets() != null)
                ? params.getRowOffsets() : new int[] {0};
        return new MultiLaneTrigger(offsets, targetsGraves(params));
    }

    // Star/omni shooters, homing plants, and lane-bouncing bulbs act whenever any zombie is on-screen.
    public static TriggerStrategy forGlobal() {
        return new GlobalTrigger();
    }

    // Melee reach (Bonk Choy, Chomper, Phat Beet, Kiwibeast): fire when a zombie is within the
    // plant's own strike box. Uses the largest configured stage so late-game reach is respected.
    public static TriggerStrategy forMelee(AbilityParams params) {
        int rowRadius = lastOrZero(params == null ? null : params.getRowRadiusByStage());
        int colRadius = lastOrDefault(params == null ? null : params.getColRadiusByStage(), 1);
        return new models.entities.plants.abilities.triggers.AreaTrigger(rowRadius, colRadius);
    }

    // Traps and mines detonate when a zombie steps onto the plant's tile.
    public static TriggerStrategy forContact() {
        return new ContactTrigger();
    }

    // Producers and passive auras that should act on a fixed cadence regardless of zombies.
    public static TriggerStrategy always() {
        return new AlwaysTrueTrigger();
    }

    private static int lastOrZero(int[] arr) {
        return lastOrDefault(arr, 0);
    }

    private static int lastOrDefault(int[] arr, int fallback) {
        if (arr == null || arr.length == 0) {
            return fallback;
        }
        return arr[arr.length - 1];
    }
}
