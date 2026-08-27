package server;

import net.Protocol;
import utils.gameinitializers.GameInitializer;
import utils.storage.DatabaseManager;
import utils.storage.LocalFileBackend;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

// The standalone server process. The Phase 3 counterpart to Main and PvZGame.
//
// Like both of those it is a composition root and nothing else: it loads the game data, starts the
// listener, wires the feature handlers, and owns the shutdown. It holds no rules of its own, because
// every rule it needs already exists in models/ and controllers/ and is shared verbatim with the two
// front ends.
//
// Started with `gradlew runServer`, optionally `-Dpvz.port=7788`.
public final class ServerMain {

    private ServerMain() { }

    public static void main(String[] args) {
        int port = resolvePort();

        // The registries have to be populated before anything reads a template, and the server DOES
        // read them: a match builds real Plants and Zombies through the same factories the client
        // does, off the same data/*.json. Skipping this does not fail here -- it fails much later, as
        // a match where no entity can be created.
        //
        // Relative paths, so the process must have been started in the project root. The runServer
        // task pins workingDir for exactly this reason.
        new GameInitializer().loadAllData();
        if (!reportLoadedData()) {
            System.err.println("[server] refusing to start: no plant or zombie templates were loaded.");
            System.err.println("[server] the server must be started FROM THE PROJECT ROOT -- data/ is "
                    + "opened with a relative path.");
            System.exit(1);
            return;
        }

        // Profile.setCurrencyObserver is deliberately NOT set. It is the hook that renders a balance
        // change to a player, and there is nobody here to render to; the model null-checks it, so
        // leaving it unclaimed is the correct answer rather than an oversight. The single static field
        // also means only one implementation could ever hold it -- another reason the server must not
        // take it.

        installAccountStore();

        GameServer server = new GameServer(port);
        try {
            server.start();
        } catch (IOException e) {
            System.err.println("[server] could not bind port " + port + ": " + e.getMessage());
            System.err.println("[server] is another copy already running? Try -Dpvz.port=<other>");
            System.exit(1);
            return;
        }

        registerFeatures(server);

        System.out.println("[server] Plants vs. Zombies 2 -- protocol v" + Protocol.VERSION);
        System.out.println("[server] ready on port " + server.port() + "; Ctrl-C to stop");

        awaitShutdown(server);
    }

    // Where each feature area wires itself in. Empty for now by design: T3.2 is the transport and the
    // session lifecycle, and a packet whose handler is not registered yet is refused with a clear
    // message rather than hanging (see GameServer.dispatch, which fails closed).
    //
    //   T3.3  accounts   -- register / login / recovery / profile sync   (AuthLevel.ANONYMOUS x4)
    //   T3.4  leaderboard                                                (AuthLevel.AUTHENTICATED)
    //   T3.5  lobby      -- online list, challenges, the random queue    (AuthLevel.AUTHENTICATED)
    //   T3.7  matches    -- commands, leaves                             (AuthLevel.AUTHENTICATED)
    //   T3.9  reactions                                                  (AuthLevel.AUTHENTICATED)
    private static void registerFeatures(GameServer server) {
        new server.auth.AccountService(server).registerHandlers();
        new server.match.MatchService(server).registerHandlers();
    }

    // Point the shared DatabaseManager at this machine's own file, BEFORE anything calls getInstance().
    //
    // Order matters and the failure is silent: getInstance() builds a default LocalFileBackend on the
    // client's users_database.json if nobody has said otherwise, and it would then be too late -- the
    // server would be serving the wrong roster, correctly, forever.
    //
    // A separate file from the client's on purpose. A server and a terminal build share a working
    // directory constantly during development, and two processes writing one save file is how a
    // player's progress disappears.
    private static void installAccountStore() {
        DatabaseManager.setBackend(new LocalFileBackend(LocalFileBackend.SERVER_FILE));
        System.out.println("[server] accounts: " + LocalFileBackend.SERVER_FILE + " ("
                + DatabaseManager.getInstance().getAllUsers().size() + " registered)");
    }

    // Counts what the registries actually ended up holding, and refuses to start on an empty one.
    //
    // The registries load from data/*.json through RELATIVE paths, so a server started from the wrong
    // directory loads nothing at all -- and every initializer here is tolerant of a missing file. It
    // would bind its port, report itself ready, accept sign-ins, and then fail only when the first
    // match tried to create a zombie and got null back. Turning that into four numbers on the console
    // is the difference between a one-line diagnosis and an afternoon.
    private static boolean reportLoadedData() {
        int plants = utils.registry.PlantRegistry.getInstance().getAllPlantTemplates().size();
        int zombies = utils.registry.ZombieRegistry.getInstance().getZombieTemplatesByAlias().size();
        int levels = utils.registry.LevelRegistry.getInstance().getAll().size();
        int quests = utils.registry.QuestRegistry.getInstance().getAllQuestTemplates().size();
        System.out.println("[server] loaded " + plants + " plants, " + zombies + " zombies, "
                + levels + " levels, " + quests + " quests");
        return plants > 0 && zombies > 0;
    }

    // -Dpvz.port, falling back to the protocol default. A bad value is reported rather than silently
    // becoming 0, which would bind a random port the clients could never guess.
    private static int resolvePort() {
        String raw = System.getProperty(Protocol.PORT_PROPERTY);
        if (raw == null || raw.isBlank()) {
            return Protocol.DEFAULT_PORT;
        }
        try {
            int port = Integer.parseInt(raw.trim());
            if (port < 0 || port > 65535) {
                throw new NumberFormatException(raw);
            }
            return port;
        } catch (NumberFormatException e) {
            System.err.println("[server] -D" + Protocol.PORT_PROPERTY + "=" + raw
                    + " is not a port; using " + Protocol.DEFAULT_PORT);
            return Protocol.DEFAULT_PORT;
        }
    }

    // Parks the main thread until the JVM is asked to stop.
    //
    // Deliberately NOT a stdin read loop. The server is not a REPL, and nothing on this path may block
    // on stdin -- the same rule the graphical build follows, for the same reason: `gradlew runServer`
    // sets no standardInput, so a read would see EOF immediately and the server would exit the instant
    // it finished starting up.
    //
    // The accept and connection threads are all daemons, so this latch is what actually keeps the
    // process alive.
    private static void awaitShutdown(GameServer server) {
        CountDownLatch stopped = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[server] shutting down...");
            server.close();
            stopped.countDown();
        }, "server-shutdown"));
        try {
            stopped.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            server.close();
        }
    }
}
