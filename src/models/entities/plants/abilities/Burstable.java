package models.entities.plants.abilities;

// A shooting ability that can be told to fire an extra rapid volley (used by PROJECTILE_BURST plant food).
public interface Burstable {
    void queueBurst(int shots);
}
