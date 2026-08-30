package models.entities.projectiles;

public enum ProjectileType {
    NORMAL_PEA,
    ICE_PEA,
    FIRE_PEA,
    GOO,
    HEAVY_CHARGED,
    BOWLING_BULB,
    MAGIC_BOLT,
    THORN,
    STAR,
    SPORE,
    FUME,
    CABBAGE,
    CORN_KERNEL,
    BUTTER,
    MELON,
    WINTER_MELON,
    PEPPER,
    RUTABAGA,
    PLASMA_BALL,
    // Citron's two shots. Its ordinary one is a citrus orb that stops at what it hits; the one plant
    // food gives it is a plasma orb that stops at nothing and clears the lane. They were both
    // PLASMA_BALL, which is Bowling Bulb's plant-food ball, so all three were drawn as the same thing.
    CITRUS_ORB,
    CITRUS_PLASMA_ORB,
    PIERCING_SPIKE,
    // A Grapeshot pellet: it does not fly in a straight line but ricochets from zombie to zombie,
    // handled entirely by Projectile's grape path rather than the normal collision loop.
    GRAPE,

}