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
    // Grabs whatever steps on it and drags it under (Tangle Kelp). Not an explosion, for the same
    // reason SQUASH_LEAP is not one: see TangleKelpAbility.
    GRAB_UNDERWATER,
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
    MINT_FAMILY_BOOST,
    // A 3x3 instant blast that also scatters bouncing grape pellets (Grapeshot).
    GRAPESHOT,
    // Jumps onto a zombie and flattens it (Squash). Not an explosion: see SquashAbility for why the
    // difference is worth a type of its own rather than a 0x0 blast radius.
    SQUASH_LEAP
}
