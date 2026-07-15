package models.entities.plants.abilities;

// A wramp-up ability whose growth stage can be maxed instantly (used by InstantGrowing plant food).
public interface Growable {
    void growToMaxStage();
}
