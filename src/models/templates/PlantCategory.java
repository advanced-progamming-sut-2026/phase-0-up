package models.templates;

// The behavioural family a plant belongs to. Mirrors the "category" field in data/plants.json
// and also drives which TriggerStrategy the factory attaches to a plant's ability.
public enum PlantCategory {
    SUN_PRODUCER,
    SHOOTER,
    HOMING,
    STRIKE_THROUGH,
    LOBBER,
    EXPLOSIVE,
    MELEE,
    WALL_NUT,
    MODIFIER
}
