package views.gdx.screens;

import com.badlogic.gdx.InputProcessor;
import models.game.Faction;
import models.game.GameSession;
import models.game.gamemodes.VersusIZombieMode;
import views.gdx.bridge.CommandBridge;
import views.gdx.input.KeyboardZombieController;
import views.gdx.ui.GameOverlays;

// Everything about GameScreen that only exists in a two-player game on ONE device.
//
// The sibling of MatchPlay, and the two are worth reading together: both are a versus match, both play
// by identical rules, and what each adds to GameScreen is only the part that is true of it and false
// of an ordinary level. MatchPlay adds a conversation with a server. This adds a second pair of hands
// and a scoreboard that has to name a side rather than a winner, because both players are looking at
// the same screen.
final class CouchPlay {

    private final GameSession session;
    private final KeyboardZombieController keyboard;

    CouchPlay(GameSession session, CommandBridge commands) {
        this.session = session;
        this.keyboard = new KeyboardZombieController(mode(session), commands);
    }

    // Added to GameScreen's InputMultiplexer alongside the mouse. They cannot collide: one reads keys
    // and the other reads clicks, which is exactly why this split is the one that works on a sofa.
    InputProcessor input() {
        return keyboard;
    }

    // Where the zombie player's cursor is, so the board can mark it. The plant player's is the mouse
    // pointer and needs no help; the keyboard player has nothing to look at without this.
    views.gdx.map.GridPos cursor() {
        return keyboard.cursor();
    }

    // Neither player is "you", so the panel names the side that won instead of congratulating one of
    // the two people reading it. This is the one place couch play cannot reuse the single-player
    // wording -- "The Zombies Ate Your Brains" is addressed to somebody, and here it would be wrong
    // for one of them whichever way it went.
    void raiseOutcome(GameOverlays overlays, boolean sessionWon) {
        VersusIZombieMode versus = mode(session);
        Faction winner = versus == null ? null : versus.winner();
        boolean zombies = winner == null ? sessionWon : winner == Faction.ZOMBIES;
        overlays.showMatchOutcome(true,
                zombies ? "Zombies Win!" : "Plants Win!",
                zombies
                        ? "Every brain eaten. Swap sides and go again?"
                        : "The lawn held. Swap sides and go again?");
    }

    private static VersusIZombieMode mode(GameSession session) {
        return session != null && session.getMode() instanceof VersusIZombieMode versus
                ? versus : null;
    }
}
