package net.dto;

// What an EntityState in a snapshot is a picture of. The client's reconciler switches on this to
// decide which factory builds the mirror entity and which list it belongs in.
public enum EntityKind {
    PLANT,
    ZOMBIE,
    PROJECTILE,
    SUN,
    PLANT_FOOD,
    MOWER,
    VASE
}
