package models.game;

// Which side of the lawn a player is playing in a two-player match.
//
// It started life in net/ as part of the wire vocabulary, and moved down here the moment the MODEL
// needed to name a side: VersusIZombieMode has to answer "who won", and models may not import net..
// (MvcBoundaryTest.modelsAreFreeOfTheNetwork). The alternative was a second enum in models plus a
// mapping between the two, which is one more place for PLANTS and ZOMBIES to get swapped -- in a mode
// whose entire subtlety is that its win condition is inverted for one of them.
//
// net/ may depend on models/, so the packets keep using this one and nothing translates.
public enum Faction {
    PLANTS,
    ZOMBIES;

    public Faction opposite() {
        return this == PLANTS ? ZOMBIES : PLANTS;
    }
}
