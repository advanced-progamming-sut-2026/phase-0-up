package models.entities.plants.abilities;

// A warm-up ability that passes through growth stages -- a Sun-shroom getting bigger and producing
// more sun, a Kiwibeast working up to its full swing. InstantGrowing plant food jumps it to the end.
public interface Growable {
    void growToMaxStage();

    // Which stage the plant is in RIGHT NOW, zero-based. Read-only, and read by the view: several
    // plants are animated as one clip set per stage, so without this the renderer has no way to know
    // which of them to play. Nothing about growth is decided here -- this only reports it.
    int growthStage();
}
