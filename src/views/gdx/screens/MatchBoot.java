package views.gdx.screens;

import controllers.engine.GameEngine;
import factories.MinigameFactory;
import models.game.Faction;
import models.game.GameSession;
import models.game.SeedPacket;
import models.templates.PlantTemplate;
import models.user.Profile;
import net.dto.CardOffer;
import net.packets.MatchStart;
import utils.registry.PlantRegistry;
import views.Renderers;

// Everything a GameScreen needs to show a match it is not running.
//
// The sibling of DevBoot, and for the same reason: GameScreen's real constructor takes a session and
// an engine, and the interesting part of starting a board is deciding what those should BE. For a
// versus match the answer is unusual enough to be worth a name -- a session that is never ticked, an
// engine that is never advanced, and a driver that applies snapshots instead of simulating.
//
// ## The mirror session
//
// A real GameSession, built from the same MinigameFactory call the server made, so its map, its mode
// and its seed packets are the ones the server is simulating. What it never gets is startMode(): the
// sun makers, the brains and the opening banks all happened on the server, and doing them again here
// would put a second set of zombies on a board the first snapshot is about to describe.
//
// ## The engine that does nothing
//
// GameScreen constructs a CommandBridge, a MinigameHarness and a Showcase around an engine, and all
// three want a non-null one. This engine is real and never ticked -- NetLoopDriver does not call
// advanceOneTick, and the command bridge is pointed at the socket rather than at engine::submit. It
// exists so the screen does not have to be rewritten around a null.
public final class MatchBoot {

    private final MatchStart start;
    private final GameSession session;
    private final GameEngine engine;

    private MatchBoot(MatchStart start, GameSession session, GameEngine engine) {
        this.start = start;
        this.session = session;
        this.engine = engine;
    }

    public static MatchBoot from(MatchStart start, Renderers renderers) {
        GameSession session = new GameSession(new Profile(),
                MinigameFactory.createVersusIZombie(start.matchDurationTicks()));
        applySeedBank(session, start);
        return new MatchBoot(start, session, new GameEngine(session, renderers));
    }

    // The plant player's cards come from the SERVER's list rather than from the mode's own, even
    // though both sides build the same mode. Only one of them is the one being charged: if the two
    // ever disagree the client draws a card the server will refuse, which is a bug the player
    // experiences as the game ignoring their clicks.
    private static void applySeedBank(GameSession session, MatchStart start) {
        if (start.plantSeedBank() == null) {
            return;
        }
        session.getSelectedSeeds().clear();
        for (CardOffer offer : start.plantSeedBank()) {
            PlantTemplate template = PlantRegistry.getInstance().getTemplateByName(offer.type());
            session.addSeed(new SeedPacket(offer.type(),
                    template == null ? 0 : (int) Math.round(template.getRecharge())));
        }
    }

    public MatchStart start() {
        return start;
    }

    public GameSession session() {
        return session;
    }

    public GameEngine engine() {
        return engine;
    }

    public Faction faction() {
        return start.yourFaction();
    }

    public String opponent() {
        return start.opponentUsername();
    }

    public String matchId() {
        return start.matchId();
    }
}
