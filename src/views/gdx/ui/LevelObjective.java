package views.gdx.ui;

import models.game.GameSession;

// What the card at the start of a level says it wants.
//
// ## Why this is its own class
//
// Two reasons, and the second is the real one. GameScreen was at Checkstyle's 500-NCSS ceiling again --
// the same ceiling that lifted InputCheck out of it -- and this is a self-contained paragraph of text
// with no screen state in it, so it was the cheapest thing to move. But it also belonged out here
// anyway: deciding what a level is for is a question about the SESSION, not about the window drawing it.
//
// ## The mode gets asked first
//
// GameScreen's comment has promised a `describeObjective` hook for a long time -- "a mode with its own
// goal says so itself" -- and there was no such method; every level got the wave count or nothing. The
// hook exists now (GameMode.describeObjective) and this is what calls it.
//
// It matters most for a level with no waves to count. Beghouled, Vasebreaker and a boss fight all fell
// through to "Hold the lawn.", which is true of every level ever played and tells the player nothing
// about the one in front of them. A Zomboss level is the case that forced it: it authors no waves at
// all, and what it actually wants is a named machine brought down.
public final class LevelObjective {

    // The one line every level ends on, whatever else it asks for. Kept here rather than appended by
    // each mode so no mode can forget it, and so it cannot drift into four slightly different wordings.
    private static final String HOUSE = "\nDon't let a zombie reach your house!";

    private LevelObjective() { }

    public static String of(GameSession session) {
        String own = session == null || session.getMode() == null
                ? null : session.getMode().describeObjective(session);
        return (own != null ? own : waveGoal(session)) + HOUSE;
    }

    // The default for every ordinary level: survive the waves it ships. "Hold the lawn." remains the
    // last resort, for a level that neither counts waves nor describes itself.
    private static String waveGoal(GameSession session) {
        int waves = session == null || session.getLevel() == null
                ? 0 : session.getLevel().getWaveCount();
        if (waves <= 0) {
            return "Hold the lawn.";
        }
        return "Survive " + waves + (waves == 1 ? " wave" : " waves") + " of zombies.";
    }
}
