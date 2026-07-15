package factories.plant;

import models.entities.plants.FoodStrategies.AttractAndHealStrategy;
import models.entities.plants.FoodStrategies.BlueFlameStrategy;
import models.entities.plants.FoodStrategies.BoostReflectStrategy;
import models.entities.plants.FoodStrategies.BowlingBulbBurstStrategy;
import models.entities.plants.FoodStrategies.BurstShootStrategy;
import models.entities.plants.FoodStrategies.CompositePlantFoodStrategy;
import models.entities.plants.FoodStrategies.DestroyZombiesStrategy;
import models.entities.plants.FoodStrategies.ElectricThornsStrategy;
import models.entities.plants.FoodStrategies.GiantPeaStrategy;
import models.entities.plants.FoodStrategies.GrantArmorStrategy;
import models.entities.plants.FoodStrategies.HomingBarrageStrategy;
import models.entities.plants.FoodStrategies.InstantArmStrategy;
import models.entities.plants.FoodStrategies.InstantGrowing;
import models.entities.plants.FoodStrategies.InstantSunProductionStrategy;
import models.entities.plants.FoodStrategies.KnockbackBlastStrategy;
import models.entities.plants.FoodStrategies.LaneClearStrategy;
import models.entities.plants.FoodStrategies.LaneFreezeStrategy;
import models.entities.plants.FoodStrategies.LobBarrageStrategy;
import models.entities.plants.FoodStrategies.LocalAoeAttackStrategy;
import models.entities.plants.FoodStrategies.MapWideButterStrategy;
import models.entities.plants.FoodStrategies.MapWideFreezeStrategy;
import models.entities.plants.FoodStrategies.MeleeFlurryStrategy;
import models.entities.plants.FoodStrategies.MoveLaneZombiesStrategy;
import models.entities.plants.FoodStrategies.MultiDisarmStrategy;
import models.entities.plants.FoodStrategies.PlantFoodStrategy;
import models.entities.plants.FoodStrategies.RandomHypnotizeStrategy;
import models.entities.plants.FoodStrategies.ResetLifespanStrategy;
import models.entities.plants.FoodStrategies.SpawnClonesStrategy;
import models.templates.PlantFoodType;
import models.templates.PlantTemplate.PlantFoodSpec;

import java.util.List;

// Turns a template's list of plant-food specs into a single CompositePlantFoodStrategy that runs
// each sub-effect together. Each PlantFoodType maps to the strategy class that implements it.
public final class FoodStrategyFactory {
    private FoodStrategyFactory() { }

    public static CompositePlantFoodStrategy build(List<PlantFoodSpec> specs) {
        CompositePlantFoodStrategy composite = new CompositePlantFoodStrategy();
        if (specs == null) {
            return composite;
        }
        for (PlantFoodSpec spec : specs) {
            composite.addStrategy(strategyFor(spec.getType(), (int) Math.round(spec.getValue())));
        }
        return composite;
    }

    private static PlantFoodStrategy strategyFor(PlantFoodType type, int value) {
        switch (type) {
            case SPAWN_SUN_ITEMS: return new InstantSunProductionStrategy(value);
            case INSTANT_GROWING: return new InstantGrowing();
            case PROJECTILE_BURST: return new BurstShootStrategy(value);
            case GIANT_PEA_BURST: return new GiantPeaStrategy(value);
            case LANE_FREEZE: return new LaneFreezeStrategy(value);
            case LANE_CLEAR: return new LaneClearStrategy(value);
            case RANDOM_HYPNOTIZE: return new RandomHypnotizeStrategy(value);
            case DESTROY_RANDOM: return new DestroyZombiesStrategy(value, true);
            case PULL_UNDERWATER: return new DestroyZombiesStrategy(value, false);
            case EXPLOSIVE_BULB_BURST: return new BowlingBulbBurstStrategy(value);
            case ELECTRIC_THORNS: return new ElectricThornsStrategy(value);
            case KNOCKBACK_BLAST: return new KnockbackBlastStrategy(value);
            case RESET_LIFESPAN: return new ResetLifespanStrategy(value);
            case MAP_WIDE_BUTTER: return new MapWideButterStrategy(value);
            case LOB_BARRAGE: return new LobBarrageStrategy(value);
            case MELEE_FLURRY: return new MeleeFlurryStrategy(value);
            case LOCAL_AOE_ATTACK: return new LocalAoeAttackStrategy(value);
            case MAP_WIDE_FREEZE: return new MapWideFreezeStrategy(value);
            case INSTANT_ARM: return new InstantArmStrategy();
            case SPAWN_CLONES: return new SpawnClonesStrategy(value);
            case GRANT_PERMANENT_ARMOR: return new GrantArmorStrategy(value);
            case BOOST_REFLECT: return new BoostReflectStrategy(value);
            case MOVE_LANE_ZOMBIES: return new MoveLaneZombiesStrategy();
            case ATTRACT_AND_HEAL: return new AttractAndHealStrategy();
            case BLUE_FLAME: return new BlueFlameStrategy(value);
            case MULTI_DISARM: return new MultiDisarmStrategy(value);
            case HOMING_BARRAGE: return new HomingBarrageStrategy(value);
            default: return null;
        }
    }
}
