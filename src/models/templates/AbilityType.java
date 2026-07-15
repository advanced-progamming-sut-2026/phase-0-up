package models.templates;

// Discriminator for a plant's active ability. Mirrors the "abilityType" field in data/plants.json;
// PlantAbilityFactory switches on this to build the matching PlantAbility.
public enum AbilityType {
    PRODUCE_SUN,
    INSTANT_SUN_BURST,
    SHOOT_PROJECTILE,
    MULTILANE_SHOOT,
    MULTI_DIRECTIONAL_SHOOT,
    HYPNOTIZE_RANDOM_TARGET,
    DAMAGE_RANDOM_TARGET,
    BOWLING_BULB,
    KERNEL_PULT,
    DELAYED_EXPLOSIVE,
    INSTANT_EXPLOSIVE,
    FREEZE_ON_CONTACT,
    MELEE_ATTACK,
    PASSIVE_SHIELD,
    REFLECT_DAMAGE,
    REPEL_ZOMBIE,
    ATTRACT_ZOMBIE,
    DEATH_EXPLOSIVE,
    PASSIVE_MODIFIER,
    MAGNET,
    HYPNOTIZE_ON_EATEN,
    MODIFIER_UTILITY,
    INSTANT_FREEZE,
    WARMTH,
    GRAVE_BUSTER,
    MINT_FAMILY_BOOST
}
