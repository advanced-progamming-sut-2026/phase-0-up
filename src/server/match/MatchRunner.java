package server.match;

import controllers.engine.GameEngine;
import factories.MinigameFactory;
import models.game.Faction;
import models.game.GameSession;
import models.game.GameState;
import models.game.Level;
import models.game.gamemodes.VersusIZombieMode;
import models.templates.PlantTemplate;
import models.user.Profile;
import models.user.User;
import net.dto.CardOffer;
import net.dto.MatchEndReason;
import net.packets.CommandRejected;
import net.packets.GameCommand;
import net.packets.MatchEvent;
import net.packets.MatchStart;
import server.ClientSession;
import server.view.RelayRenderers;
import utils.Constants;
import utils.Result;
import utils.registry.PlantRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

// The authoritative simulation of one match: the real GameEngine, ticked at the game's own rate, with
// two clients feeding it commands and reading snapshots back.
//
// ## The one hard rule
//
// Only this class's tick task touches the GameSession. Commands arrive on two connection reader
// threads and are parked in a ConcurrentLinkedQueue; they are drained at the top of a tick, on the
// tick thread, and applied there. Nothing in models/ is thread-safe -- not one collection, not one
// counter -- so a command applied from a reader thread while the engine is halfway through a tick is
// a ConcurrentModificationException at best and a board that quietly disagrees with itself at worst.
//
// ## No reimplementation of anything
//
// advanceOneTick() is the same method the single-player graphical build calls from its accumulator,
// running the same systems in the same order against the same model. That is only possible because
// MvcBoundaryTest keeps models/ and controllers/ free of LibGDX, and it is the reason the server can
// be trusted: there is no second copy of the rules to drift.
public final class MatchRunner {

    private record Submission(ClientSession from, String text) { }

    // 100 ms, from Constants.TICKS_PER_SECOND rather than written out: every speed, cooldown and
    // interval in the game is expressed in these ticks, so the wall-clock rate has to be derived from
    // the same number or the whole game runs at the wrong speed.
    private static final long TICK_MILLIS = 1000L / Constants.TICKS_PER_SECOND;

    private final Match match;
    private final ScheduledExecutorService clock;
    private final Consumer<Match> onEnded;
    private final Consumer<String> log;

    private final Level level;
    private final GameSession session;
    private final VersusIZombieMode mode;
    private final RelayRenderers renderers;
    private final GameEngine engine;
    private final SnapshotBuilder snapshots = new SnapshotBuilder();

    private final Queue<Submission> inbound = new ConcurrentLinkedQueue<>();

    private volatile ScheduledFuture<?> ticking;
    private volatile boolean stopped;

    MatchRunner(Match match, int durationTicks, ScheduledExecutorService clock,
                Consumer<Match> onEnded, Consumer<String> log) {
        this.match = match;
        this.clock = clock;
        this.onEnded = onEnded;
        this.log = log;

        this.level = MinigameFactory.createVersusIZombie(durationTicks);
        this.mode = (VersusIZombieMode) level.getGameMode();
        // A fresh, neutral Profile -- neither player's.
        //
        // Two reasons, and both are bugs if it is skipped. The match stays fair: GameSession.plant
        // reads getPlantsLevels() for the plant's level, so one player's upgraded Peashooter would
        // outshoot the other's identical card. And nothing in the end-of-level path can reach a real
        // account: GameEngine.announceOutcome saves the profile it is given, and CampaignSystem marks
        // a level complete on it.
        this.session = new GameSession(new Profile(), level);
        this.renderers = new RelayRenderers(result -> match.broadcast(event(result)));
        this.engine = new GameEngine(session, renderers);
    }

    // ---- lifecycle -------------------------------------------------------------------------------

    // Called AFTER both clients have been sent their MatchStart. init() places the sun makers and
    // queues the mode's opening banner, and that banner must not arrive before the packet that tells a
    // client there is a match at all.
    void start() {
        engine.init();
        match.broadcast(snapshots.build(session));
        ticking = clock.scheduleAtFixedRate(this::safeTick, TICK_MILLIS, TICK_MILLIS,
                TimeUnit.MILLISECONDS);
    }

    void stop() {
        stopped = true;
        ScheduledFuture<?> current = ticking;
        if (current != null) {
            current.cancel(false);
        }
    }

    // Everything a client needs before the first snapshot: which side it is on, both rosters, the
    // board's shape and the clock. Per-recipient -- the two clients get different packets.
    MatchStart startFor(Faction faction, String opponent) {
        return new MatchStart(match.id(), faction, opponent, mode.matchDurationTicks(),
                offers(mode.getRoster()), plantOffers(), mode.getRedLineColumn(),
                Constants.BOARD_ROWS, Constants.BOARD_COLS,
                mode.startingPlantSun(), mode.startingZombieSun());
    }

    // A player action, straight off the reader thread. Queued, never applied here.
    void submit(ClientSession from, GameCommand command) {
        if (stopped || command == null || command.text() == null) {
            return;
        }
        inbound.add(new Submission(from, command.text()));
    }

    // ---- the tick --------------------------------------------------------------------------------

    // A ScheduledExecutorService CANCELS a repeating task the first time it throws, and says nothing.
    // An unhandled exception in a tick would therefore not crash the match -- it would freeze it, with
    // two players watching a still board and no error anywhere. So the tick is wrapped, and a failure
    // ends the match loudly instead.
    private void safeTick() {
        if (stopped) {
            return;
        }
        try {
            tick();
        } catch (RuntimeException | Error failure) {
            log.accept("match " + match.id() + " failed mid-tick: " + failure);
            stop();
            conclude(Faction.PLANTS, MatchEndReason.SERVER_SHUTDOWN);
        }
    }

    private void tick() {
        drainCommands();
        engine.advanceOneTick();
        match.broadcast(snapshots.build(session));

        if (mode.winner() != null || session.getState() != GameState.PLAYING) {
            stop();
            conclude(mode.winner(), reasonOf(mode.ending()));
        }
    }

    private void drainCommands() {
        Submission submission;
        while ((submission = inbound.poll()) != null) {
            apply(submission);
        }
    }

    private void apply(Submission submission) {
        ClientSession from = submission.from();
        Faction faction = match.factionOf(from);
        String refusal = FactionCommands.refusalFor(faction, submission.text());
        if (refusal != null) {
            from.send(new CommandRejected(submission.text(), refusal));
            return;
        }
        // Whatever this command reports belongs to the player who sent it -- "Not enough sun for a
        // Peashooter" is an answer to one person's click, and broadcasting it would tell their opponent
        // what they just tried and could not afford. The tick's own narration goes to both.
        renderers.directTo(from, result -> from.send(result.success()
                ? event(result)
                : new CommandRejected(submission.text(), result.message())));
        try {
            engine.submitInGameCommand(submission.text());
        } finally {
            renderers.broadcastAgain();
        }
    }

    // ---- ending ----------------------------------------------------------------------------------

    // Called when the mode has decided, and also from MatchService when a socket drops. Idempotent
    // through Match.end, which is guarded -- both players quitting at the same instant must not each
    // be told they won.
    void conclude(Faction winner, MatchEndReason reason) {
        Faction decided = winner == null ? Faction.PLANTS : winner;
        MatchEndReason decidedReason = reason == null ? MatchEndReason.TIME_UP : reason;
        // Out of the registry FIRST. A match stops being live the instant it is decided, not once the
        // profile write behind it has finished -- and the lobby is what the other players are asking.
        // The other order let a client receive its MatchOver and then still be told, for the length of
        // a disk write, that both players were busy.
        onEnded.accept(match);
        if (match.end(decided, decidedReason, mode.brainsEaten(), mode.brainsTotal(),
                session.getTimeTicks())) {
            recordResult(decided);
        }
    }

    // The only thing about a match that outlives it: each player's versus record. Written to the LIVE
    // User out of the server's roster -- the same instance the leaderboard reads -- and then flushed,
    // because a crash between the match ending and the next profile sync would otherwise lose it.
    private void recordResult(Faction winner) {
        credit(match.plants(), winner == Faction.PLANTS);
        credit(match.zombies(), winner == Faction.ZOMBIES);
        try {
            utils.storage.DatabaseManager.getInstance().saveAll();
        } catch (RuntimeException failure) {
            log.accept("could not save the result of match " + match.id() + ": " + failure);
        }
    }

    private void credit(ClientSession session, boolean won) {
        User user = session.user();
        if (user == null || user.getProfile() == null) {
            return;   // signed out between the last tick and the result; nothing to write to
        }
        user.getProfile().recordVersusResult(won);
    }

    private static MatchEndReason reasonOf(VersusIZombieMode.Ending ending) {
        if (ending == null) {
            return MatchEndReason.TIME_UP;
        }
        return switch (ending) {
            case BRAINS_EATEN -> MatchEndReason.BRAINS_EATEN;
            case TIME_UP -> MatchEndReason.TIME_UP;
            case HORDE_SPENT -> MatchEndReason.HORDE_SPENT;
        };
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private static MatchEvent event(Result result) {
        return new MatchEvent(result.success(), result.message());
    }

    private static List<CardOffer> offers(java.util.Map<String, Integer> roster) {
        List<CardOffer> cards = new ArrayList<>();
        roster.forEach((type, cost) -> cards.add(new CardOffer(type, cost)));
        return cards;
    }

    // The plant player's bank, priced from the registry rather than from a table here. A cost written
    // down twice is a cost that eventually disagrees with itself, and the client would charge one
    // number while the server charged another.
    private List<CardOffer> plantOffers() {
        List<CardOffer> cards = new ArrayList<>();
        for (String name : mode.preSelectedPlants()) {
            PlantTemplate template = PlantRegistry.getInstance().getTemplateByName(name);
            cards.add(new CardOffer(name, template == null ? 0 : template.getCost()));
        }
        return cards;
    }

    // ---- inspection (tests) ----------------------------------------------------------------------

    public GameSession session() {
        return session;
    }

    public VersusIZombieMode mode() {
        return mode;
    }
}
