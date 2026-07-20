package models.entities.interactables;

// What a Vasebreaker vase hides until it is broken. A vase is EMPTY, holds a ZOMBIE, or holds a
// one-use SEED_PACKET for a plant. (SUN / PLANT remain for other interactable uses.)
public enum VaseContent {
    EMPTY,
    ZOMBIE,
    SUN,
    PLANT,
    SEED_PACKET
}
