package models.game.scoring;

// The five ways a player earns Meow Points in the scoring game. Each carries its own award and the label
// the end-of-game breakdown prints, so the rules are declared in one readable place rather than as
// scattered magic numbers inside the engine.
public enum MeowPointRule {

    // Two or more zombies dying inside the same one-second window -- a wave wiped in one blow.
    SIMULTANEOUS_KILL("Simultaneous Kill", 50),

    // A zombie put down within five seconds of walking on: it barely got to move.
    SPEED_KILL("Speed Kill", 30),

    // An explosive plant felling a zombie outright, from full health.
    ONE_SHOT("One-Shot Kill", 20),

    // Sun still unspent when the level ends: one point per ten sun.
    SUN_HOARDER("Sun Hoarder", 1),

    // Every lawn mower still sitting untouched at the end.
    FLAWLESS_DEFENSE("Flawless Defense", 100);

    private final String label;
    private final int award;

    MeowPointRule(String label, int award) {
        this.label = label;
        this.award = award;
    }

    public String getLabel() {
        return label;
    }

    // Meow Points granted each time this rule fires.
    public int getAward() {
        return award;
    }
}
