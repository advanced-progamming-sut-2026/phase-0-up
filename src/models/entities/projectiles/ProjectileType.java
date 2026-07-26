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
    PIERCING_SPIKE,
    // A Grapeshot pellet: it does not fly in a straight line but ricochets from zombie to zombie,
    // handled entirely by Projectile's grape path rather than the normal collision loop.
    GRAPE,

}