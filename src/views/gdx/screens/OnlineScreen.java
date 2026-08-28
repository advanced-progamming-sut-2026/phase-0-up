package views.gdx.screens;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import net.PacketType;
import net.dto.ChallengeRejectReason;
import models.game.Faction;
import net.packets.AckResponse;
import net.packets.ChallengeDeclined;
import net.packets.ChallengeRejected;
import net.packets.ChallengeRequest;
import net.packets.OnlineUsersRequest;
import net.packets.OnlineUsersResponse;
import net.packets.QueueJoinRequest;
import net.packets.QueueLeaveRequest;
import net.packets.QueueStatus;
import views.gdx.core.GdxContext;
import views.gdx.ui.MenuStyles;

import java.util.List;

// The multiplayer lobby: the two ways the spec says a match can start.
//
//   Challenge a specific player -- type their name, or click one off the list of who is around.
//   Random match               -- join a queue and wait for the next person who does the same.
//
// ## What this screen is not responsible for
//
// It does not decide who is available, and it does not decide what "offline" means. Both are facts only
// the server holds -- a player on this list may have started a match a tenth of a second ago -- so the
// list is what the server last said, and every refusal comes back from the server with a reason. The
// screen's job is to say those reasons in the game's voice.
//
// The invite POP-UP is not here either. A challenge can arrive while the player is in the shop or the
// almanac, so it is raised on the ModalLayer by PvZGame, which is above every screen. This screen only
// ever SENDS them.
public final class OnlineScreen extends MenuScreen {

    // How often the "who is around" list refreshes itself. Frequent enough that somebody signing in
    // appears without the player wondering whether the screen is broken; rare enough that a lobby full
    // of idle players is not a request per frame.
    private static final float REFRESH_SECONDS = 3f;
    private static final float LIST_WIDTH = 420f;
    private static final float LIST_MAX_HEIGHT = 220f;

    private TextField opponent;
    private Table playerList;
    private Label status;
    private TextButton queueButton;
    private TextButton sideButton;

    // Which side this player wants. A preference, not a promise: if both players ask for the same, the
    // server decides, so a match can always start rather than deadlocking on a wish.
    private Faction preferred = Faction.ZOMBIES;

    private boolean queued;
    private float sinceRefresh;

    public OnlineScreen(GdxContext context) {
        super(context);
    }

    @Override
    protected void build(Table root) {
        Table panel = MenuPanel.build(skin, "Multiplayer");

        if (!context.game().isOnline()) {
            buildOfflineNotice(root, panel);
            return;
        }

        status = MenuStyles.label(skin, "Pick a fight.", MenuStyles.TEXT);
        panel.add(status).width(LIST_WIDTH).padBottom(12f).row();
        panel.add(MenuStyles.label(skin, "Who's around", MenuStyles.HEADING)).padBottom(6f).row();
        playerList = new Table();
        playerList.top();
        ScrollPane pane = new ScrollPane(playerList, skin);
        pane.setScrollingDisabled(true, false);
        pane.setFadeScrollBars(false);
        panel.add(pane).width(LIST_WIDTH).maxHeight(LIST_MAX_HEIGHT).padBottom(14f).row();

        opponent = MenuStyles.field(skin, "opponent's username");
        panel.add(opponent).width(LIST_WIDTH).height(52f).padBottom(10f).row();

        sideButton = MenuStyles.button(skin, sideLabel(), MenuStyles.BUTTON_BROWN);
        sideButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                preferred = preferred.opposite();
                sideButton.setText(sideLabel());
            }
        });
        panel.add(sideButton).width(LIST_WIDTH).height(50f).padBottom(10f).row();

        panel.add(action("Challenge", () -> sendChallenge(opponent.getText())))
                .width(LIST_WIDTH).height(58f).padBottom(10f).row();

        queueButton = action("Random Match", this::toggleQueue);
        panel.add(queueButton).width(LIST_WIDTH).height(58f).padBottom(10f).row();

        panel.add(couchButton()).width(LIST_WIDTH).height(58f).padBottom(14f).row();
        panel.add(backButton()).width(200f).height(56f).row();

        root.setFillParent(true);
        root.add(panel);

        listenForRefusals();
        refreshPlayers();

        // Two windows, one queue, no hands. See DebugFlags.AUTO_QUEUE -- the versus mode cannot be
        // exercised end to end any other way.
        if (views.gdx.core.DebugFlags.AUTO_QUEUE) {
            toggleQueue();
        }
    }

    // Said plainly and early. Offering a lobby that cannot work would let the player type a name,
    // press a button and get nothing, with no idea why.
    private void buildOfflineNotice(Table root, Table panel) {
        panel.add(MenuStyles.label(skin,
                "No server. Multiplayer needs one -- but the sofa doesn't.",
                MenuStyles.TEXT)).width(LIST_WIDTH).padBottom(18f).row();
        // Offered here as well, and this is the branch it matters most on: couch play is the whole of
        // multiplayer for somebody with no server, so a lobby that only said "no" would be hiding the
        // one thing that still works.
        panel.add(couchButton()).width(LIST_WIDTH).height(58f).padBottom(14f).row();
        panel.add(backButton()).width(200f).height(56f).row();
        root.setFillParent(true);
        root.add(panel);
    }

    // Two players, one keyboard, no server at all. The mouse plants and WASD summons -- see
    // KeyboardZombieController, and CouchBoot for how little the rest of the game has to know.
    private TextButton couchButton() {
        TextButton button = MenuStyles.button(skin, "Couch Play (2 on this PC)",
                MenuStyles.BUTTON_BROWN);
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                leaveLobby();
                context.game().openCouchMatch();
            }
        });
        return button;
    }

    // The two green buttons that do something to the server. Both are a label and a Runnable, and
    // spelling out an anonymous ChangeListener for each was four lines of ceremony saying so twice.
    private TextButton action(String label, Runnable onPress) {
        TextButton button = MenuStyles.button(skin, label, MenuStyles.BUTTON_GREEN);
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                onPress.run();
            }
        });
        return button;
    }

    private TextButton backButton() {
        TextButton back = MenuStyles.button(skin, "Back", MenuStyles.BUTTON_PURPLE);
        back.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                goBack();
            }
        });
        return back;
    }

    private String sideLabel() {
        return "I want to play: " + (preferred == Faction.ZOMBIES ? "Zombies" : "Plants");
    }

    // ---- talking to the server ------------------------------------------------------------------

    // The two pushes that belong to THIS screen: a challenge that could not be delivered, and one that
    // was turned down. Both are answers to something the player did here, so they are reported here.
    //
    // The invite itself is claimed by PvZGame, not by this screen, because it has to work when this
    // screen is not open.
    private void listenForRefusals() {
        pushRouter().on(PacketType.CHALLENGE_REJECTED, envelope ->
                say(refusalText(envelope.as(ChallengeRejected.class))));
        pushRouter().on(PacketType.CHALLENGE_DECLINED, envelope -> {
            ChallengeDeclined declined = envelope.as(ChallengeDeclined.class);
            say(declined.timedOut()
                    ? declined.byUsername() + " never answered. Try someone else."
                    : declined.byUsername() + " turned you down. Rude.");
        });
        pushRouter().on(PacketType.QUEUE_STATUS, envelope ->
                setQueued(envelope.as(QueueStatus.class).waiting()));
    }

    // Each reason gets its own sentence. The spec asks for "an appropriate error" when the username is
    // invalid or the player is offline, and one message for both would leave the player unable to tell
    // a typo from a friend who is asleep -- which are fixed in completely different ways.
    private String refusalText(ChallengeRejected rejected) {
        String name = rejected.targetUsername();
        ChallengeRejectReason reason = rejected.reason();
        if (reason == null) {
            return "That challenge went nowhere.";
        }
        return switch (reason) {
            case NO_SUCH_USER -> "No gardener called \"" + name + "\". Check the spelling.";
            case OFFLINE -> name + " isn't online right now.";
            case IN_MATCH -> name + " is already in a match. Give them a minute.";
            case SELF -> "You can't challenge yourself. That's just gardening.";
            case ALREADY_PENDING -> "You've already got a challenge out there.";
        };
    }

    // Every request on this screen goes through here, and none of them may be made any other way.
    //
    // NetClient.request BLOCKS until the answer comes back or the timeout expires, and every caller
    // below is either a Scene2D click listener or the render() method -- all of which run on the one
    // thread that draws the window. A blocking call there freezes the game for the whole round trip,
    // and that is not a theoretical cost: joining the queue when somebody is already waiting gets a
    // MatchStart PUSH instead of a reply, so the request waits out its FULL timeout while the packets
    // that would have opened the lawn queue up behind the frozen render thread. The match started on
    // the server and the client sat in the lobby.
    //
    // So the round trip happens on its own thread and the answer is handed back through
    // postRunnable -- the same shape PvZGame.answerChallenge uses, for the same reason. `reply` is
    // null when nothing came back, and every callback below has to say so rather than assume.
    private <T extends net.Packet> void ask(net.Packet request, Class<T> expected,
                                            java.util.function.Consumer<T> then) {
        Thread worker = new Thread(() -> {
            T reply = context.game().netClient().request(request, expected);
            com.badlogic.gdx.Gdx.app.postRunnable(() -> {
                if (!closed) {
                    then.accept(reply);
                }
            });
        }, "lobby-request");
        worker.setDaemon(true);
        worker.start();
    }

    // Whether this screen has gone away. An answer that arrives after it has would otherwise set text
    // on a Label belonging to a disposed stage.
    private boolean closed;

    private void sendChallenge(String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (!MenuForms.require(context, name, "Type a username to challenge.")) {
            return;
        }
        say("Asking " + name + "...");
        ask(new ChallengeRequest(name, preferred), AckResponse.class, ack -> {
            if (ack == null) {
                // A refused challenge comes back as CHALLENGE_REJECTED, which the push handler above
                // reports. A null here means the request itself did not get an answer at all.
                say("The server isn't answering.");
                return;
            }
            say(ack.ok() ? "Waiting for " + name + " to answer..." : ack.message());
        });
    }

    private void toggleQueue() {
        if (queued) {
            setQueued(false);
            say("Left the queue.");
            context.game().netClient().send(new QueueLeaveRequest());
            return;
        }
        say("Looking for an opponent...");
        ask(new QueueJoinRequest(preferred), QueueStatus.class, joined -> {
            if (joined == null) {
                // Not necessarily a failure: if somebody was already waiting the server starts the
                // match immediately and answers with a MatchStart PUSH rather than a queue status, so
                // there is nothing to report here and the match screen has already taken over.
                return;
            }
            setQueued(joined.waiting());
            say(joined.waiting()
                    ? "In the queue. Waiting for another gardener..."
                    : "Match found!");
        });
    }

    private void setQueued(boolean queued) {
        this.queued = queued;
        if (queueButton != null) {
            queueButton.setText(queued ? "Cancel Search" : "Random Match");
        }
    }

    private void refreshPlayers() {
        ask(new OnlineUsersRequest(), OnlineUsersResponse.class, response -> {
            if (response != null && playerList != null) {
                renderPlayers(response.usernames());
            }
        });
    }

    private void renderPlayers(List<String> names) {
        playerList.clearChildren();
        if (names.isEmpty()) {
            playerList.add(MenuStyles.label(skin, "Nobody else is online. Lonely lawn.",
                    MenuStyles.TEXT)).pad(16f).row();
            return;
        }
        for (String name : names) {
            TextButton pick = MenuStyles.button(skin, name, MenuStyles.BUTTON_BROWN);
            pick.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    // Fills the field rather than challenging outright: a mis-click that fires off a
                    // challenge is a pop-up on a stranger's screen, and there is no way to take it back.
                    opponent.setText(name);
                }
            });
            playerList.add(pick).width(LIST_WIDTH - 24f).height(44f).padBottom(4f).row();
        }
    }

    private void say(String message) {
        if (status != null) {
            status.setText(message);
        }
    }

    private views.net.PushRouter pushRouter() {
        return context.game().pushRouter();
    }

    // ---- lifecycle ------------------------------------------------------------------------------

    @Override
    public void render(float delta) {
        super.render(delta);
        if (playerList == null) {
            return;
        }
        sinceRefresh += delta;
        if (sinceRefresh >= REFRESH_SECONDS) {
            sinceRefresh = 0f;
            refreshPlayers();
        }
    }

    @Override
    public void hide() {
        super.hide();
        leaveLobby();
    }

    @Override
    public void dispose() {
        leaveLobby();
        super.dispose();
    }

    // Releasing the push claims is not optional: a handler left registered would go on setting text on
    // a Label belonging to a screen that has been disposed. Leaving the QUEUE matters just as much --
    // a player who walks away from this screen is not waiting for a match any more, and pairing them
    // with somebody who IS waiting would drop a match on a menu that cannot show it.
    private void leaveLobby() {
        closed = true;
        if (playerList == null) {
            return;
        }
        pushRouter().off(PacketType.CHALLENGE_REJECTED, PacketType.CHALLENGE_DECLINED,
                PacketType.QUEUE_STATUS);
        if (queued && context.game().isOnline()) {
            context.game().netClient().send(new QueueLeaveRequest());
            queued = false;
        }
    }

    @Override
    protected void goBack() {
        commands.back();
    }
}
