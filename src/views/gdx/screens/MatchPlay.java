package views.gdx.screens;

import models.game.Faction;
import models.social.Reaction;
import net.PacketType;
import net.packets.CommandRejected;
import net.packets.GameCommand;
import net.packets.MatchEvent;
import net.packets.MatchOver;
import net.packets.MatchSnapshot;
import net.packets.OpponentDisconnected;
import net.packets.ReactionRelay;
import net.packets.ReactionSend;
import utils.Result;
import views.gdx.bridge.NetLoopDriver;
import views.gdx.core.GdxContext;
import views.gdx.ui.GameOverlays;
import views.gdx.ui.ReactionBar;
import views.gdx.ui.ReactionPopup;
import views.renderers.InGameRenderer;
import views.net.PushRouter;

// Everything about GameScreen that only exists in a two-player match.
//
// Lifted out of GameScreen for the reason InputCheck was before it: that class has a 500-statement
// ceiling and the versus wiring pushed it over. The split is not arbitrary, though -- what lives here
// is exactly the part that is true of a match and false of every single-player lawn, so the branch is
// "is there a MatchPlay" rather than a dozen scattered flags.
//
// GameScreen still owns the board, the renderers and the HUD. This owns the CONVERSATION: the six
// packets a match speaks in, the reaction bar and popup, and the fact of who won -- which on a client
// is a thing you are told, not a thing you can work out.
final class MatchPlay {

    private final GdxContext context;
    private final MatchBoot boot;
    private final NetLoopDriver loop;
    private final InGameRenderer narration;

    private final ReactionBar reactionBar;
    private final ReactionPopup reactionPopup;

    // How the match ended, once the server has said so. Null while it is still being played, and that
    // is what the outcome panel is gated on -- the mirror's GameState is meaningless here, because
    // this client's board is never simulated and never leaves PLAYING.
    private MatchOver result;

    MatchPlay(GdxContext context, MatchBoot boot, NetLoopDriver loop, InGameRenderer narration,
              com.badlogic.gdx.scenes.scene2d.Stage hudStage) {
        this.context = context;
        this.boot = boot;
        this.loop = loop;
        this.narration = narration;
        this.reactionPopup = new ReactionPopup(context.assets(), context.sprites(), hudStage);
        this.reactionBar = new ReactionBar(context.assets(), context.sprites(), hudStage,
                this::sendReaction);
        listen();
    }

    // ---- the conversation ------------------------------------------------------------------------

    // Claims the six packets a match speaks in. Released in release(): a handler left registered would
    // go on writing to a HUD belonging to a screen that no longer exists.
    private void listen() {
        PushRouter router = context.game().pushRouter();
        router.on(PacketType.MATCH_SNAPSHOT, envelope ->
                loop.onSnapshot(envelope.as(MatchSnapshot.class)));
        // Narration the whole board can see -- a plant eaten, a brain gone, a detonation.
        //
        // Fed to the SAME renderer the single-player build feeds, not straight to a toast, because
        // these are the same sentences: the model wrote them and the server only carried them. The
        // whole Phase 8 fan-out -- explosion flashes, ash, camera shake, audio cues, NPC lines, and
        // the toast policy that keeps most of them silent -- therefore works in a match with nothing
        // added, because all of it is downstream of this one call.
        router.on(PacketType.MATCH_EVENT, envelope -> {
            MatchEvent event = envelope.as(MatchEvent.class);
            narration.render(new Result(event.success(), event.text()));
        });
        // A refusal, from the faction whitelist or from the model itself. Always this player's own --
        // the server sends a rejection only to whoever earned it.
        router.on(PacketType.COMMAND_REJECTED, envelope ->
                context.toasts().error(envelope.as(CommandRejected.class).reason()));
        router.on(PacketType.MATCH_OVER, envelope -> {
            result = envelope.as(MatchOver.class);
            loop.onMatchOver();
        });
        router.on(PacketType.REACTION_RELAY, envelope -> {
            ReactionRelay relay = envelope.as(ReactionRelay.class);
            reactionPopup.show(relay.fromUsername(), Reaction.of(relay.kind(), relay.index()));
        });
        router.on(PacketType.OPPONENT_DISCONNECTED, envelope ->
                context.toasts().error(envelope.as(OpponentDisconnected.class).username()
                        + " dropped out. Hold on..."));
    }

    void release() {
        context.game().pushRouter().off(PacketType.MATCH_SNAPSHOT, PacketType.MATCH_EVENT,
                PacketType.COMMAND_REJECTED, PacketType.MATCH_OVER, PacketType.REACTION_RELAY,
                PacketType.OPPONENT_DISCONNECTED);
    }

    // Where a click goes in a match. Returns true unconditionally when it was sent: CommandBridge's
    // sink answers "did anything take this", and over a socket the honest answer is "it has been
    // sent". Whether the server RUNS it comes back as a CommandRejected, which is the toast above.
    boolean sendCommand(String command) {
        if (!context.game().isOnline()) {
            context.toasts().error("Lost the server.");
            return false;
        }
        context.game().netClient().send(new GameCommand(command, loop.ticksRun()));
        return true;
    }

    private void sendReaction(Reaction reaction) {
        if (context.game().isOnline()) {
            context.game().netClient().send(new ReactionSend(reaction.kind(), reaction.index()));
        }
    }

    // Forfeiting. The SERVER decides that, not this client: it awards the win to the other player and
    // tells both. Ending the local board instead would quietly leave the opponent playing nobody.
    void leave() {
        if (result == null && context.game().isOnline()) {
            context.game().netClient().send(new net.packets.MatchLeaveRequest());
        }
    }

    // The real frame delta, not the animation one: an opponent's message is not part of the
    // simulation and must not hang on this player's screen because they happen to have paused.
    void update(float delta) {
        reactionPopup.update(delta);
        runReactionCheck();
    }

    // -Dpvz.reactionCheck=N: fires reaction N once, a second in, and leaves the bar open.
    //
    // The half of this feature worth checking is what the OTHER player sees, and no single window can
    // reach it -- so one client is given this flag and the other is screenshotted. See DebugFlags.
    private int reactionCheckFrames;

    private void runReactionCheck() {
        int wanted = views.gdx.core.DebugFlags.REACTION_CHECK;
        if (wanted < 0 || wanted >= Reaction.values().length) {
            return;
        }
        reactionCheckFrames++;
        if (reactionCheckFrames != 60) {
            return;
        }
        reactionBar.open();
        sendReaction(Reaction.values()[wanted]);
    }

    // ---- the result ------------------------------------------------------------------------------

    boolean isOver() {
        return result != null;
    }

    // Which of the two spec-verbatim banners belongs to THIS player.
    //
    // The server sends neither. The mode's win condition is written from the zombie player's seat, so
    // one shared banner would tell whichever player won by holding the lawn that a zombie ate their
    // brain. MatchOver names the winning FACTION and each client compares it against its own side --
    // from the server, both statements are true at once.
    private boolean won() {
        return result != null && result.winner() == boot.faction();
    }

    void raiseOutcome(GameOverlays overlays) {
        boolean won = won();
        context.audio().play(won ? views.gdx.core.AudioManager.SFX_WIN
                : views.gdx.core.AudioManager.SFX_LOSE);
        overlays.showMatchOutcome(won, title(won), body());
    }

    // Four titles, not two. Winning as the plant player and winning as the zombie player are opposite
    // achievements -- one held a lawn, the other ate it -- and a match has somebody on both ends of
    // every result, so the panel has to say which one this player is looking at.
    private String title(boolean won) {
        boolean plants = boot.faction() == Faction.PLANTS;
        if (won) {
            return plants ? "The Lawn Holds!" : "Brainz Acquired!";
        }
        return plants ? "The Zombies Ate Your Brains" : "Your Horde Fell Apart";
    }

    private String body() {
        if (result == null) {
            return "";
        }
        String reason = switch (result.reason()) {
            case BRAINS_EATEN -> "Every brain eaten.";
            case TIME_UP -> "The clock ran out with " + brainsLeft() + " still standing.";
            case HORDE_SPENT -> "The horde ran out of zombies and sun.";
            case OPPONENT_LEFT -> boot.opponent() + " left the lawn.";
            case SERVER_SHUTDOWN -> "The server went away.";
        };
        return reason + (won()
                ? "\nYou take it. " + boot.opponent() + " will want a rematch."
                : "\n" + boot.opponent() + " takes this one. Go again?");
    }

    private String brainsLeft() {
        int left = result.brainsTotal() - result.brainsEaten();
        return left + (left == 1 ? " brain" : " brains");
    }

    void dispose() {
        release();
        reactionBar.dispose();
        reactionPopup.dispose();
    }
}
