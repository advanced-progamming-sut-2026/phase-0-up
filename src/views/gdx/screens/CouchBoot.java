package views.gdx.screens;

import controllers.engine.GameEngine;
import factories.MinigameFactory;
import models.game.GameSession;
import models.user.Profile;
import views.Renderers;

// Two players, one device, no server.
//
// The sibling of MatchBoot, and the point of putting them side by side is how little they differ: the
// same VersusIZombieMode, the same board, the same win conditions, the same command strings. What
// changes is only who is producing those commands and who is running the simulation.
//
//   MatchBoot   -- the SERVER simulates; this client mirrors snapshots; one player per machine.
//   CouchBoot   -- THIS machine simulates, with an ordinary GameLoopDriver; both players are here.
//
// So there is no reconciler, no snapshot stream and no network at all -- just a normal live session
// that happens to be driven by two sets of hands. That the mode needed no changes to support it is the
// evidence that the versus rules really are in the model rather than in the network layer.
final class CouchBoot {

    private final GameSession session;
    private final GameEngine engine;

    private CouchBoot(GameSession session, GameEngine engine) {
        this.session = session;
        this.engine = engine;
    }

    static CouchBoot start(Renderers renderers) {
        // A fresh, neutral Profile, for the reason MatchRunner uses one: GameSession.plant reads
        // getPlantsLevels() for the plant's level, so playing on somebody's account would give their
        // upgraded Peashooter a real advantage over the identical card on the other side of the sofa.
        GameSession session = new GameSession(new Profile(), MinigameFactory.createVersusIZombie());
        GameEngine engine = new GameEngine(session, renderers);
        engine.init();
        return new CouchBoot(session, engine);
    }

    GameSession session() {
        return session;
    }

    GameEngine engine() {
        return engine;
    }
}
