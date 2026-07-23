package factories.plant;

import models.entities.plants.Plant;
import models.entities.plants.abilities.AreaExplosiveAbility;
import models.entities.plants.abilities.DeathExplosiveAbility;
import models.entities.plants.abilities.FreezeOnContactAbility;
import models.entities.plants.abilities.GlobalTargetingAbility;
import models.entities.plants.abilities.HypnotizeOnEatenAbility;
import models.entities.plants.abilities.InstantFreezeAbility;
import models.entities.plants.abilities.InstantSunBurstAbility;
import models.entities.plants.abilities.KernelPultAbility;
import models.entities.plants.abilities.MagnetAbility;
import models.entities.plants.abilities.MeleeAttackAbility;
import models.entities.plants.abilities.MintFamilyBoostAbility;
import models.entities.plants.abilities.PlantAbility;
import models.entities.plants.abilities.ProduceSunAbility;
import models.entities.plants.abilities.ReflectDamageAbility;
import models.entities.plants.abilities.ShootProjectileAbility;
import models.entities.plants.abilities.WarmthAbility;
import models.entities.projectiles.Element;
import models.templates.PlantTemplate;
import models.templates.PlantTemplate.UpgradeSpec;
import utils.Constants;

import java.util.ArrayList;
import java.util.List;

// Turns a plant's per-level upgrade list into concrete effects. The BUFF_* kinds fold into a set of
// effective stats used to build the plant; the SPECIAL_MECHANIC kinds are applied afterwards to the
// already-built abilities (see applySpecialMechanics), keyed by their specialTag.
public final class UpgradeResolver {
    private static final int TORCHWOOD_DEATH_EXPLOSION_DAMAGE = 300;

    private UpgradeResolver() { }

    // Folds every upgrade whose level has been reached into the plant's effective stats.
    public static EffectiveStats resolve(PlantTemplate template, int level) {
        int hp = template.getBaseHp();
        int cost = template.getCost();
        int damageBuff = 0;
        double intervalSeconds = template.getActionInterval();
        double recharge = template.getRecharge();
        List<UpgradeSpec> specials = new ArrayList<>();

        if (template.getUpgrades() != null) {
            for (UpgradeSpec upgrade : template.getUpgrades()) {
                if (upgrade.getLevel() > level) {
                    continue;
                }
                double value = upgrade.getValue();
                switch (upgrade.getType()) {
                    case BUFF_HP: hp += (int) value; break;
                    case BUFF_COST: cost += (int) value; break;
                    case BUFF_DAMAGE: damageBuff += (int) value; break;
                    case BUFF_ACTION_INTERVAL: intervalSeconds += value; break;
                    case BUFF_RECHARGE: recharge += value; break;
                    case SPECIAL_MECHANIC: specials.add(upgrade); break;
                    default: break;
                }
            }
        }

        int intervalTicks = (int) Math.round(Math.max(0.0, intervalSeconds) * Constants.TICKS_PER_SECOND);
        return new EffectiveStats(Math.max(0, hp), intervalTicks, Math.max(0, cost),
                Math.max(0.0, recharge), damageBuff, specials);
    }

    // Convenience for the seed-packet layer: the recharge (seconds) a plant's card should use at a level.
    public static double effectiveRecharge(PlantTemplate template, int level) {
        return resolve(template, level).getRecharge();
    }

    // Applies each SPECIAL_MECHANIC upgrade to the plant's freshly-built abilities.
    public static void applySpecialMechanics(Plant plant, List<UpgradeSpec> mechanics) {
        if (mechanics == null) {
            return;
        }
        for (UpgradeSpec spec : mechanics) {
            applyMechanic(plant, spec.getSpecialTag(), spec.getValue());
        }
    }

    // Split into sun-economy upgrades and combat upgrades so each switch stays inside the 50-line
    // method limit. applySunMechanic reports whether it handled the tag; anything it does not own
    // falls through to the combat switch.
    private static void applyMechanic(Plant plant, String tag, double value) {
        if (tag == null) {
            return;
        }
        if (!applySunMechanic(plant, tag, value)) {
            applyCombatMechanic(plant, tag, value);
        }
    }

    // Upgrades to sun production and the plant-food economy.
    private static boolean applySunMechanic(Plant plant, String tag, double value) {
        switch (tag) {
            case "DOUBLE_SUN_CHANCE": set(plant, ProduceSunAbility.class, a -> a.setDoubleSunChance(value)); break;
            case "GROW_TIME_REDUCTION": set(plant, ProduceSunAbility.class,
                    a -> a.reduceStageUpTicks(secondsToTicks(Math.abs(value)))); break;
            case "SUN_DROP_INCREMENT":
                set(plant, ProduceSunAbility.class, a -> a.increaseSunAmounts((int) value)); break;
            case "SUN_AMOUNT_BUFF":
                set(plant, InstantSunBurstAbility.class, a -> a.increaseSunAmount((int) value)); break;
            case "AUTO_PLANT_FOOD_CHANCE": plant.setAutoPlantFoodChance(value); break;
            default: return false;
        }
        return true;
    }

    // Upgrades to how a plant fights: shots, reach, damage, status effects and death effects.
    private static void applyCombatMechanic(Plant plant, String tag, double value) {
        int ticks = secondsToTicks(value);
        switch (tag) {
            case "PRIORITIZE_GARGANTUARS":
                set(plant, GlobalTargetingAbility.class, a -> a.setPrioritizeGargantuars(true)); break;
            case "ADDITIONAL_PIERCE":
                set(plant, ShootProjectileAbility.class, a -> a.increasePierce((int) value)); break;
            case "SPLASH_DAMAGE_BUFF":
                set(plant, ShootProjectileAbility.class, a -> a.increaseSplashDamage((int) value)); break;
            case "BUTTER_CHANCE_BUFF": set(plant, KernelPultAbility.class, a -> a.increaseButterChance(value)); break;
            case "REFLECT_DAMAGE_BUFF": set(plant, ReflectDamageAbility.class, a -> a.boostReflect((int) value)); break;
            case "MELT_AREA_3X3": set(plant, WarmthAbility.class, a -> a.setRadius(1, 1)); break;
            case "BONUS_SMASH_CHARGES": case "BONUS_GRAB_TARGETS":
                set(plant, AreaExplosiveAbility.class, a -> a.widenArea(0, (int) value)); break;
            case "ZOMBIE_HEALTH_MULTIPLIER":
                set(plant, HypnotizeOnEatenAbility.class, a -> a.setZombieHealthMultiplier(value)); break;
            case "ZOMBIE_DAMAGE_MULTIPLIER":
                set(plant, HypnotizeOnEatenAbility.class, a -> a.setZombieDamageMultiplier(value)); break;
            case "DURATION_EXT": set(plant, MintFamilyBoostAbility.class, a -> a.extendDuration(ticks)); break;
            case "TILE_RANGE_EXT": applyTileRangeExt(plant, value); break;
            case "FREEZE_DURATION_EXT": applyFreezeDurationExt(plant, ticks); break;
            case "CHILL_DURATION_EXT":
                set(plant, ShootProjectileAbility.class, a -> a.increaseChillDuration(ticks)); break;
            case "POISON_TICK_BUFF":
                set(plant, ShootProjectileAbility.class, a -> a.increasePoisonDps((int) value)); break;
            case "GROWTH_STAGE_MAX_UP":
                set(plant, MeleeAttackAbility.class, MeleeAttackAbility::addGrowthStage); break;
            case "GRAPE_BOUNCE_EXT":
                set(plant, AreaExplosiveAbility.class, a -> a.widenArea(0, (int) value)); break;
            case "WARM_RADIUS_EXT":
                set(plant, WarmthAbility.class, a -> a.increaseRadius((int) value)); break;
            case "LIFESPAN_EXT":
                if (plant.getHealth() != null) {
                    plant.getHealth().extendLifespan((int) value);
                }
                break;
            case "DEATH_EXPLOSION_AOE":
                plant.addAbility(new DeathExplosiveAbility(TORCHWOOD_DEATH_EXPLOSION_DAMAGE, 1, 1, Element.FIRE));
                break;
            case "EXPLODE_ON_FINISH":
                plant.addAbility(new DeathExplosiveAbility((int) value, 1, 1, Element.NEUTRAL)); break;
            default: break; // AUTO_PLANTFOOD_ON_ENTER / RESET_FAMILY_COOLDOWNS are seed-layer concerns
        }
    }

    // TILE_RANGE_EXT widens whichever ranged ability the plant owns (shooter reach or magnet pull).
    private static void applyTileRangeExt(Plant plant, double value) {
        set(plant, ShootProjectileAbility.class, a -> a.increaseMaxRange(value));
        set(plant, MagnetAbility.class, a -> a.increaseRange(value));
    }

    // FREEZE_DURATION_EXT covers both the trap freeze and the board-wide freeze.
    private static void applyFreezeDurationExt(Plant plant, int ticks) {
        set(plant, FreezeOnContactAbility.class, a -> a.extendFreeze(ticks));
        set(plant, InstantFreezeAbility.class, a -> a.extendFreeze(ticks));
    }

    private static int secondsToTicks(double seconds) {
        return (int) Math.round(seconds * Constants.TICKS_PER_SECOND);
    }

    // Applies an action to every ability of the given type the plant owns.
    private static <T> void set(Plant plant, Class<T> type, AbilityAction<T> action) {
        if (plant.getAbilities() == null) {
            return;
        }
        for (PlantAbility ability : plant.getAbilities()) {
            if (type.isInstance(ability)) {
                action.apply(type.cast(ability));
            }
        }
    }

    @FunctionalInterface
    private interface AbilityAction<T> {
        void apply(T ability);
    }
}
