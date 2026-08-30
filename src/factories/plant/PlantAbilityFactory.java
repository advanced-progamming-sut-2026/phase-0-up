package factories.plant;

import models.entities.plants.abilities.AttractZombieAbility;
import models.entities.plants.abilities.BowlingBulbAbility;
import models.entities.plants.abilities.DamageRandomTargetAbility;
import models.entities.plants.abilities.DeathExplosiveAbility;
import models.entities.plants.abilities.DelayedExplosiveAbility;
import models.entities.plants.abilities.FreezeOnContactAbility;
import models.entities.plants.abilities.GraveBusterAbility;
import models.entities.plants.abilities.SquashAbility;
import models.entities.plants.abilities.HypnotizeOnEatenAbility;
import models.entities.plants.abilities.HypnotizeRandomTargetAbility;
import models.entities.plants.abilities.GrapeshotAbility;
import models.entities.plants.abilities.InstantExplosiveAbility;
import models.entities.plants.abilities.InstantFreezeAbility;
import models.entities.plants.abilities.InstantSunBurstAbility;
import models.entities.plants.abilities.KernelPultAbility;
import models.entities.plants.abilities.MagnetAbility;
import models.entities.plants.abilities.MeleeAttackAbility;
import models.entities.plants.abilities.MintFamilyBoostAbility;
import models.entities.plants.abilities.MultiDirectionalShootAbility;
import models.entities.plants.abilities.MultiLaneShootAbility;
import models.entities.plants.abilities.PassiveModifierAbility;
import models.entities.plants.abilities.PassiveShieldAbility;
import models.entities.plants.abilities.PlantAbility;
import models.entities.plants.abilities.ProduceSunAbility;
import models.entities.plants.abilities.ReflectDamageAbility;
import models.entities.plants.abilities.RepelZombieAbility;
import models.entities.plants.abilities.ShootProjectileAbility;
import models.entities.plants.abilities.ShootDirection;
import models.entities.plants.abilities.TangleKelpAbility;
import models.entities.plants.abilities.TargetingPriority;
import models.entities.plants.abilities.WarmthAbility;
import models.entities.projectiles.Element;
import models.entities.projectiles.ProjectileType;
import models.entities.projectiles.Trajectory;
import utils.Constants;
import models.templates.AbilityType;
import models.templates.PlantTemplate.AbilityParams;

// Builds the concrete PlantAbility for a given AbilityType from its typed params. Damage-carrying
// abilities receive an already-buffed scalar damage plus the raw damage buff (so stage arrays can be
// bumped uniformly). Returns null for MODIFIER_UTILITY (Imitater), which has no in-game ability.
public final class PlantAbilityFactory {
    private PlantAbilityFactory() { }

    // Split across two switches so each stays inside the 50-statement NCSS limit; the first returns
    // null for a type it does not own and the second picks it up.
    public static PlantAbility build(AbilityType type, AbilityParams params,
                                     int actionIntervalTicks, int scalarDamage, int damageBuff) {
        PlantAbility ability = buildOffensive(type, params, actionIntervalTicks, scalarDamage, damageBuff);
        return ability != null ? ability : buildSupport(type, params, actionIntervalTicks, scalarDamage);
    }

    private static PlantAbility buildOffensive(AbilityType type, AbilityParams params,
                                               int actionIntervalTicks, int scalarDamage, int damageBuff) {
        switch (type) {
            case PRODUCE_SUN: return produceSun(params, actionIntervalTicks);
            case INSTANT_SUN_BURST: return new InstantSunBurstAbility(params.getSunAmount(), spawnCount(params));
            case SHOOT_PROJECTILE: return shoot(params, actionIntervalTicks, scalarDamage);
            case MULTILANE_SHOOT: return multiLane(params, actionIntervalTicks, scalarDamage);
            case MULTI_DIRECTIONAL_SHOOT: return multiDirectional(params, actionIntervalTicks, scalarDamage);
            case HYPNOTIZE_RANDOM_TARGET: return new HypnotizeRandomTargetAbility(
                    actionIntervalTicks, TriggerResolver.forGlobal(), priority(params), params.getPriorityRange());
            case DAMAGE_RANDOM_TARGET: return new DamageRandomTargetAbility(
                    actionIntervalTicks, TriggerResolver.forGlobal(), priority(params),
                    params.getPriorityRange(), scalarDamage);
            case BOWLING_BULB: return new BowlingBulbAbility(actionIntervalTicks, TriggerResolver.forGlobal(),
                    params.getCyanReloadTicks(), params.getBlueReloadTicks(), params.getOrangeReloadTicks());
            case KERNEL_PULT: return new KernelPultAbility(actionIntervalTicks, TriggerResolver.forShooter(params),
                    params.getKernelDamage() + damageBuff, params.getButterDamage(), params.getSpeedX());
            case DELAYED_EXPLOSIVE: return new DelayedExplosiveAbility(actionIntervalTicks,
                    TriggerResolver.forContact(), scalarDamage, params.getExplosionRowRadius(),
                    params.getExplosionColRadius(), element(params));
            case GRAB_UNDERWATER: return new TangleKelpAbility(actionIntervalTicks,
                    TriggerResolver.forContact(), scalarDamage, params.getExplosionRowRadius(),
                    params.getExplosionColRadius(), element(params));
            case INSTANT_EXPLOSIVE: return instantExplosive(params, scalarDamage);
            case SQUASH_LEAP: return new SquashAbility(actionIntervalTicks,
                    TriggerResolver.forContact(), scalarDamage, element(params));
            case GRAPESHOT: return new GrapeshotAbility(scalarDamage,
                    params.getExplosionRowRadius(), params.getExplosionColRadius(), element(params),
                    Constants.GRAPESHOT_BASE_BOUNCES);
            case FREEZE_ON_CONTACT: return new FreezeOnContactAbility(actionIntervalTicks,
                    TriggerResolver.forContact(), params.getFreezeDurationTicks());
            case MELEE_ATTACK: return melee(params, actionIntervalTicks, damageBuff);
            default: return null;
        }
    }

    // Split out only because of the crater. A bomb that scorches its own tile is one boolean in the
    // data (Doom-shroom's), and the ability has to be built before it can be told.
    private static PlantAbility instantExplosive(AbilityParams params, int scalarDamage) {
        InstantExplosiveAbility ability = new InstantExplosiveAbility(scalarDamage,
                params.getExplosionRowRadius(), params.getExplosionColRadius(), element(params));
        ability.setLeavesCrater(params.isLeavesCrater());
        return ability;
    }

    // Split out for the same reason instantExplosive is: whether the plant is used up by its own thaw
    // is one boolean in the data, and the ability has to be built before it can be told.
    private static PlantAbility warmth(AbilityParams params, int actionIntervalTicks) {
        WarmthAbility ability = new WarmthAbility(actionIntervalTicks, TriggerResolver.always(),
                params.getRowRadius(), params.getColRadius());
        ability.setConsumedOnUse(params.isConsumedOnUse());
        return ability;
    }

    private static PlantAbility buildSupport(AbilityType type, AbilityParams params,
                                             int actionIntervalTicks, int scalarDamage) {
        switch (type) {
            case PASSIVE_SHIELD: return new PassiveShieldAbility();
            case REFLECT_DAMAGE: return new ReflectDamageAbility(params.getReflectDamage());
            case REPEL_ZOMBIE: return new RepelZombieAbility();
            case ATTRACT_ZOMBIE: return new AttractZombieAbility(actionIntervalTicks, TriggerResolver.always());
            case DEATH_EXPLOSIVE: return new DeathExplosiveAbility(scalarDamage,
                    params.getExplosionRowRadius(), params.getExplosionColRadius(), element(params));
            case PASSIVE_MODIFIER: return new PassiveModifierAbility(element(params), params.getDamageMultiplier());
            case MAGNET: return new MagnetAbility(actionIntervalTicks, TriggerResolver.forGlobal(), params.getRange());
            case HYPNOTIZE_ON_EATEN: return new HypnotizeOnEatenAbility();
            case INSTANT_FREEZE: return new InstantFreezeAbility(params.getFreezeDurationTicks());
            case WARMTH: return warmth(params, actionIntervalTicks);
            case GRAVE_BUSTER: return new GraveBusterAbility();
            case MINT_FAMILY_BOOST: return new MintFamilyBoostAbility();
            case MODIFIER_UTILITY: return null;
            default: return null;
        }
    }

    private static PlantAbility produceSun(AbilityParams params, int actionIntervalTicks) {
        return new ProduceSunAbility(actionIntervalTicks, TriggerResolver.always(),
                copy(params.getSunAmountsByStage()), copy(params.getStageUpTicks()),
                params.getDoubleSunChance(), spawnCount(params));
    }

    private static PlantAbility shoot(AbilityParams params, int actionIntervalTicks, int scalarDamage) {
        return new ShootProjectileAbility(actionIntervalTicks, TriggerResolver.forDirection(params),
                projectile(params), scalarDamage, shotCount(params), params.getSpeedX(),
                params.getBurstDelayTicks(), params.getPierceCount(), params.getMaxRange(),
                element(params), trajectory(params), direction(params),
                params.getSplashDamage(), params.getSplashRadiusX(), params.getSplashRowRadius());
    }

    private static PlantAbility multiLane(AbilityParams params, int actionIntervalTicks, int scalarDamage) {
        return new MultiLaneShootAbility(actionIntervalTicks, TriggerResolver.forMultiLane(params),
                projectile(params), scalarDamage, params.getSpeedX(), params.getRowOffsets());
    }

    private static PlantAbility multiDirectional(AbilityParams params, int actionIntervalTicks, int scalarDamage) {
        return new MultiDirectionalShootAbility(actionIntervalTicks, TriggerResolver.forGlobal(),
                projectile(params), scalarDamage, params.getDirectionSpeeds(), shotCount(params));
    }

    private static PlantAbility melee(AbilityParams params, int actionIntervalTicks, int damageBuff) {
        int[] damageByStage = addToEach(params.getDamageByStage(), damageBuff);
        return new MeleeAttackAbility(actionIntervalTicks, TriggerResolver.forMelee(params),
                damageByStage, copy(params.getRowRadiusByStage()), copy(params.getColRadiusByStage()),
                copy(params.getStageUpTicks()), element(params));
    }

    // Every per-stage array an ability can MUTATE is copied on the way out of the template.
    //
    // PlantTemplate is one immutable blueprint shared by every instance of that plant, for the life of
    // the process -- but its int[] fields are only immutable by convention, and several upgrades write
    // through them: ProduceSunAbility.reduceStageUpTicks (Sun-shroom's level-2 "grows faster") and
    // increaseSunAmounts both edit in place. Handed the template's own array, upgrading ONE Sun-shroom
    // silently re-tuned every Sun-shroom in every later level of the session, and the effect stacked
    // each time. Caught by the showcase: shortening one mushroom's timers grew two of them.
    //
    // addToEach above already returns a fresh array, which is why the damage side never showed it.
    private static int[] copy(int[] values) {
        return values == null ? new int[0] : values.clone();
    }

    private static int[] addToEach(int[] base, int delta) {
        if (base == null) {
            return new int[0];
        }
        int[] result = new int[base.length];
        for (int i = 0; i < base.length; i++) {
            result[i] = base[i] + delta;
        }
        return result;
    }

    private static int shotCount(AbilityParams params) {
        return params.getShotCount() > 0 ? params.getShotCount() : 1;
    }

    private static int spawnCount(AbilityParams params) {
        return params.getSpawnCount() > 0 ? params.getSpawnCount() : 1;
    }

    private static ProjectileType projectile(AbilityParams params) {
        return params.getProjectileType() != null ? params.getProjectileType() : ProjectileType.NORMAL_PEA;
    }

    private static Element element(AbilityParams params) {
        return params.getElement() != null ? params.getElement() : Element.NEUTRAL;
    }

    private static Trajectory trajectory(AbilityParams params) {
        return params.getTrajectory() != null ? params.getTrajectory() : Trajectory.DIRECT;
    }

    private static ShootDirection direction(AbilityParams params) {
        return params.getDirection() != null ? params.getDirection() : ShootDirection.FORWARD;
    }

    private static TargetingPriority priority(AbilityParams params) {
        return params.getPriorityStrategy() != null ? params.getPriorityStrategy() : TargetingPriority.RANDOM;
    }
}
