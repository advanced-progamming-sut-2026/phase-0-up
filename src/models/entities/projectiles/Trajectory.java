package models.entities.projectiles;

/**
 * How a projectile travels toward its target.
 *
 * <p>{@code DIRECT} shots fly along the ground and collide with blocking terrain;
 * {@code LOBBED} (overhead) shots arc over terrain and only strike zombies.</p>
 */
public enum Trajectory {
    DIRECT,
    LOBBED
}
