package models.templates;

// Discriminator for a single plant-food sub-effect. Mirrors the "type" field of each entry in a
// plant's "plantFood" array; FoodStrategyFactory switches on this to build the matching PlantFoodStrategy.
public enum PlantFoodType {
    SPAWN_SUN_ITEMS,
    INSTANT_GROWING,
    PROJECTILE_BURST,
    GIANT_PEA_BURST,
    LANE_FREEZE,
    LANE_CLEAR,
    RANDOM_HYPNOTIZE,
    DESTROY_RANDOM,
    EXPLOSIVE_BULB_BURST,
    ELECTRIC_THORNS,
    KNOCKBACK_BLAST,
    RESET_LIFESPAN,
    MAP_WIDE_BUTTER,
    LOB_BARRAGE,
    MELEE_FLURRY,
    LOCAL_AOE_ATTACK,
    MAP_WIDE_FREEZE,
    PULL_UNDERWATER,
    INSTANT_ARM,
    SPAWN_CLONES,
    GRANT_PERMANENT_ARMOR,
    BOOST_REFLECT,
    MOVE_LANE_ZOMBIES,
    ATTRACT_AND_HEAL,
    BLUE_FLAME,
    MULTI_DISARM,
    HOMING_BARRAGE
}
