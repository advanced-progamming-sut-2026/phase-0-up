package controllers.engine;

import controllers.commands.ingame.*;
import controllers.systems.game.*;
import models.game.GameSession;
import models.game.GameState;
import utils.Result;
import utils.regex.InGameRegex;
import views.InputHandler;
import views.Renderers;
import views.renderers.InGameRenderer;
import views.renderers.MapRenderer;

public class GameEngine {
    private GameSession gameSession;
    private InGameRenderer inGameRenderer;
    private final MapRenderer mapRenderer;
    private CombatSystem combatSystem;
    private SunSystem sunSystem;
    private PlantFoodSystem plantFoodSystem;
    private TimeSystem timeSystem;
    private WaveSystem waveSystem;
    private QuestSystem questSystem;
    private EnvironmentSystem environmentSystem;
    private boolean running;

    // The View arrives from the composition root. The engine used to build a terminal renderer here,
    // which meant every level -- however it was started -- reported to stdout and nowhere else.
    public GameEngine(GameSession gameSession, Renderers renderers) {
        this.gameSession = gameSession;
        this.inGameRenderer = renderers.inGame();
        this.mapRenderer = renderers.map();
        this.combatSystem = new CombatSystem();
        this.sunSystem = new SunSystem();
        this.plantFoodSystem = new PlantFoodSystem();
        this.timeSystem = new TimeSystem();
        // The scoring game must deal every player the same lawn on a given day, so its wave system runs
        // off the day's seed instead of an unseeded Random. That single decision covers which zombies
        // each wave buys and which lanes they walk down -- everything WaveSystem randomises.
        this.waveSystem = gameSession.getMode() instanceof models.game.gamemodes.ScoringMode scoring
                ? new WaveSystem(new java.util.Random(scoring.getSeed()))
                : new WaveSystem();
        this.questSystem = new QuestSystem();
        this.environmentSystem = new EnvironmentSystem();
        this.combatSystem.setQuestSystem(questSystem);   // combat reports kills/losses to the quest tally
    }

    // Everything a level needs before its first tick, with no loop attached.
    //
    // Split out of startLoop so the same preparation can serve two very different callers: the terminal
    // build, which follows it with a blocking stdin loop, and the graphical build, where LibGDX owns
    // the loop and calls advanceOneTick() from a fixed-step accumulator instead. Neither may skip this
    // -- startMode() is what places pre-set plants, seeds the mode's banner and arms its rules.
    public void init() {
        sunSystem.reset();
        gameSession.startMode();
        gameSession.applySeedBoosts();   // carry seed-selection boosts into the live seed packets
        questSystem.startTrackingLevel(gameSession);
        // Drain what onStart queued BEFORE the first frame, or its banner arrives a tick late.
        for (Result startEvent : gameSession.drainEvents()) {
            inGameRenderer.render(startEvent);
        }
        running = true;
    }

    public void startLoop() {
        init();
        run();
    }
    public void stopLoop() {running = false;}

    // Whether the engine still considers the level live. The graphical build has no blocking loop to
    // fall out of, so it asks this instead of inferring it.
    public boolean isRunning() {
        return running && gameSession.getState() == GameState.PLAYING;
    }

    private void run() {
        while (running && gameSession.getState() == GameState.PLAYING) {
            String input = InputHandler.readLine();
            if (input == null) {   // stdin closed (EOF) -> leave the game loop instead of spinning
                running = false;
                break;
            }
            if (input.isBlank()) {
                continue;   // a bare Enter (or a line of spaces/tabs) is not a command -- just re-prompt
            }
            if (!routeAndExecute(input)) {
                inGameRenderer.render(new Result(false, "The zombies didn't understand that one. "
                        + "Try \"show map\" to get your bearings."));
            }
        }
    }

    // One frame of the game. The engine only orders the systems and renders what they report; the
    // per-entity work lives in the systems themselves.
    //
    // Order matters. The clock moves first, because every system below reads the session's tick.
    // Waves run before combat so a zombie that arrives this tick is ticked in the same frame. Win/lose
    // is evaluated last, once state has settled -- kept here rather than inside a system so
    // time-advancement and rule-evaluation never interfere.
    public void advanceOneTick() {
        timeSystem.advance(gameSession, 1);
        long currentTick = gameSession.getTimeTicks();

        for (Result sunEvent : sunSystem.onTick(gameSession)) {
            inGameRenderer.render(sunEvent);
        }
        for (Result waveEvent : waveSystem.processTick(gameSession, currentTick)) {
            inGameRenderer.render(waveEvent);
        }
        for (Result combatEvent : combatSystem.processTick(gameSession, currentTick)) {
            inGameRenderer.render(combatEvent);
        }
        // After combat, because combat is what DROPS plant food: a pickup created this tick should get
        // its full life, and ageing it before it exists would be a tick short. Its expiry line is also
        // the last word on a drop, so it belongs after the death that produced it.
        for (Result plantFoodEvent : plantFoodSystem.onTick(gameSession)) {
            inGameRenderer.render(plantFoodEvent);
        }
        // Terrain reacts after the entities have moved, so it sees where they actually ended up
        // (a zombie that just stepped onto a slider tile, ice that a fire plant is now beside, ...).
        environmentSystem.tick(gameSession);

        // Drain the model's domain-event queue and render it. Plants, zombie abilities, terrain and
        // projectiles record narrative here during the systems above instead of printing directly, so
        // the console output all funnels through the view at one controlled point.
        for (Result modelEvent : gameSession.drainEvents()) {
            inGameRenderer.render(modelEvent);
        }

        GameState before = gameSession.getState();
        gameSession.evaluateModeRules();
        announceOutcome(before, gameSession.getState());
    }

    // The level ends exactly once, so the banner is printed on the transition out of PLAYING rather
    // than from the state itself (which stays WON/LOST for every later tick).
    private void announceOutcome(GameState before, GameState after) {
        if (before != GameState.PLAYING || after == GameState.PLAYING) {
            return;
        }
        // Settle the scoring game first: two of its rules read the final board state (sun left unspent,
        // mowers never triggered), so it has to run before anything else touches it.
        settleScoringGame();

        boolean countsForQuests = gameSession.getMode() == null
                || gameSession.getMode().countsTowardQuests();
        if (after == GameState.WON) {
            inGameRenderer.render(new Result(true,
                    "Dear humanz, zis is not done yet; we will come back to eat your brainz, humanz."));
            // The level is won: evaluate quests against it and announce any that just completed (their
            // rewards are granted straight to the profile).
            if (countsForQuests) {
                for (Result quest : questSystem.evaluateAndComplete(gameSession.getPlayer(), gameSession, true)) {
                    inGameRenderer.render(quest);
                }
            }
        } else if (after == GameState.LOST) {
            inGameRenderer.render(new Result(false, "The zombie ate your brain; LOSER!!!"));
            // A loss still ends a level: quests are evaluated so the cross-level counters settle (the
            // max-difficulty win streak breaks) and any quest that doesn't need a win -- chapter kills,
            // the mowerless last-stand kills -- can still complete on what happened this level.
            if (countsForQuests) {
                for (Result quest : questSystem.evaluateAndComplete(gameSession.getPlayer(), gameSession, false)) {
                    inGameRenderer.render(quest);
                }
            }
        }

        // AFTER every profile mutation: running before quest evaluation lost finished quests.
        try {
            utils.storage.DatabaseManager.getInstance().saveAll();
        } catch (RuntimeException e) {
            inGameRenderer.render(new Result(false,
                    "Your progress could not be saved: " + e.getMessage()));
        }
    }


    // Closes out a scoring-game run: applies the end-of-level Meow Point rules, shows the player the full
    // breakdown, and keeps the score on the profile if it beat their previous best (which is what the
    // leaderboard's Meow Points column reads). A non-scoring level does nothing here.
    private void settleScoringGame() {
        if (!(gameSession.getMode() instanceof models.game.gamemodes.ScoringMode scoring)) {
            return;
        }
        int score = scoring.settleAndScore(gameSession);
        inGameRenderer.render(new Result(true, scoring.getMeowPoints().buildScorecard()));

        models.user.Profile profile = gameSession.getPlayer();
        if (profile == null) {
            return;
        }
        // Boxed, and null means this is their FIRST run -- not a previous best of zero. Unboxing it
        // into an int here would throw on every player's first scoring game, which is every player at
        // least once.
        Integer best = profile.getBestNumberOfMeowPoints();
        boolean beatIt = profile.recordScoringGameRun(score);
        // Offered to whoever keeps the record, which offline is nobody and online is the server. The
        // local profile has ALREADY been updated, deliberately: the player sees their result on the
        // frame the run ends rather than after a round trip, and the answer here is the correction if
        // the server disagrees. See AccountBackend.submitScore.
        submitScore(score);
        if (beatIt) {
            if (best == null) {
                // No "previous best: 0" for somebody who has never played -- that is the fake score
                // the whole boxed field exists to stop showing.
                inGameRenderer.render(new Result(true, "Your first score on the board: " + score
                        + " Meow Points. The leaderboard has been notified."));
            } else {
                inGameRenderer.render(new Result(true, "A new personal best! " + score
                        + " Meow Points (previous best: " + best
                        + "). The leaderboard has been notified."));
            }
        } else {
            inGameRenderer.render(new Result(true, "You scored " + score
                    + " Meow Points. Your best is still " + best + " -- go again!"));
        }
    }

    // Sends the run to the server, if there is one, and says so when the two disagree.
    //
    // A disagreement is rare and worth a sentence rather than a silent rewrite: it means this account
    // scored higher on another machine, and a player watching their new best quietly turn into a
    // bigger number with no explanation would reasonably think the game had lost their run.
    private void submitScore(int score) {
        Integer kept;
        try {
            kept = utils.storage.DatabaseManager.getInstance().submitScore(score);
        } catch (RuntimeException unreachable) {
            inGameRenderer.render(new Result(false,
                    "Your score is saved here, but the leaderboard did not hear about it."));
            return;
        }
        models.user.Profile profile = gameSession.getPlayer();
        if (kept == null || profile == null || kept.equals(profile.getBestNumberOfMeowPoints())) {
            return;
        }
        profile.setBestNumberOfMeowPoints(kept);
        inGameRenderer.render(new Result(true, "The leaderboard already had " + kept
                + " Meow Points for you from another lawn. That one still stands."));
    }

    // Abandons the match in progress. Quitting is a forfeit, so the session is put into LOST through
    // the same state change a defeat uses, and the normal end-of-level path runs on top of it: quests
    // are evaluated, a scoring run is settled, and the profile is saved. Then the loop is stopped, and
    // the caller (InputRouter.runGame) drops the player back on the Play menu.
    private void exitGame() {
        abandonLevel();
        running = false;
    }

    // Leaving a level early, from either front end.
    //
    // Public because the graphical build's "Save and Exit" needs exactly this and cannot get it any
    // other way: calling GameSession.forfeit() alone would set the state and stop there, skipping the
    // quest evaluation, the scoring-game settle and the save that make the exit count. Those all live
    // in announceOutcome, which only ever runs from inside a tick -- and a forfeited session never
    // ticks again.
    public void abandonLevel() {
        GameState before = gameSession.getState();
        if (gameSession.forfeit()) {
            inGameRenderer.render(new Result(false,
                    "You retreat from the lawn. The zombies will be telling this story for years."));
            announceOutcome(before, gameSession.getState());
        } else {
            inGameRenderer.render(new Result(true, "This lawn is already settled -- heading back."));
        }
    }

    // Where in-game command output goes. The terminal prints it; the graphical build swaps in a
    // renderer that raises a toast instead. Everything else about a command -- parsing, the rules it
    // runs, the Result it produces -- is identical either way, which is the entire point.
    public void setInGameRenderer(InGameRenderer renderer) {
        if (renderer != null) {
            this.inGameRenderer = renderer;
        }
    }

    // Runs one in-game command exactly as if it had been typed at the prompt.
    //
    // This is the seam the graphical build synthesises through: clicking a seed card and then a tile
    // produces the string "plant plant -t Peashooter -l (3, 2)" and hands it here, so the click and
    // the typed command are the SAME operation. Cost checks, cooldowns, occupied tiles, aquatic
    // rules -- none of it is reimplemented for the mouse, and none of it can drift out of sync.
    //
    // Returns false for input no pattern claims, which the caller reports however it likes.
    public boolean submitInGameCommand(String input) {
        return input != null && routeAndExecute(input);
    }

    // Dispatches one in-game command. Split into four groups by what the command acts on, so each
    // stays inside the 50-line limit and a new command has one obvious place to go. Order between the
    // groups is irrelevant -- every pattern is anchored and mutually exclusive.
    private boolean routeAndExecute(String input) {
        if (InGameRegex.EXIT_GAME.matches(input)) {
            exitGame();
            return true;
        }
        return routeSunAndTime(input)
                || routePlantCommands(input)
                || routeZombieCommands(input)
                || routeViewCommands(input);
    }

    // Sun economy and the clock.
    private boolean routeSunAndTime(String input) {
        if (InGameRegex.COLLECT_SUN.matches(input)) {
            int x = Integer.parseInt(InGameRegex.COLLECT_SUN.getGroup(input, "x"));
            int y = Integer.parseInt(InGameRegex.COLLECT_SUN.getGroup(input, "y"));
            new CollectSunCommand(gameSession, sunSystem, inGameRenderer, questSystem, x, y).execute();
            return true;
        }
        // Beside the sun rather than with the plant commands: it is the same gesture on the same lawn
        // -- name a tile, take what is lying on it -- and a player who has just learnt one has learnt
        // the other.
        if (InGameRegex.COLLECT_PLANT_FOOD.matches(input)) {
            int x = Integer.parseInt(InGameRegex.COLLECT_PLANT_FOOD.getGroup(input, "x"));
            int y = Integer.parseInt(InGameRegex.COLLECT_PLANT_FOOD.getGroup(input, "y"));
            new CollectPlantFoodCommand(gameSession, plantFoodSystem, inGameRenderer, x, y).execute();
            return true;
        }
        if (InGameRegex.SHOW_SUN_AMOUNT.matches(input)) {
            new ShowSunCommand(gameSession, inGameRenderer).execute();
            return true;
        }
        if (InGameRegex.CHEAT_ADD_SUN.matches(input)) {
            int count = Integer.parseInt(InGameRegex.CHEAT_ADD_SUN.getGroup(input, "count"));
            new AddSunCheatCommand(gameSession, inGameRenderer, count).execute();
            return true;
        }
        if (InGameRegex.ADVANCE_TIME.matches(input)) {
            int ticks = Integer.parseInt(InGameRegex.ADVANCE_TIME.getGroup(input, "count"));
            advanceTime(ticks);
            return true;
        }
        return false;
    }

    // Everything the player does to their own plants and the things that yield them.
    private boolean routePlantCommands(String input) {
        if (InGameRegex.PLANT_SEED.matches(input)) {
            String plantType = InGameRegex.PLANT_SEED.getGroup(input, "type");
            int x = Integer.parseInt(InGameRegex.PLANT_SEED.getGroup(input, "x"));
            int y = Integer.parseInt(InGameRegex.PLANT_SEED.getGroup(input, "y"));
            new PlantSeedCommand(gameSession, inGameRenderer, plantType, x, y).execute();
            return true;
        }
        if (InGameRegex.PLUCK_PLANT.matches(input)) {
            int x = Integer.parseInt(InGameRegex.PLUCK_PLANT.getGroup(input, "x"));
            int y = Integer.parseInt(InGameRegex.PLUCK_PLANT.getGroup(input, "y"));
            new PluckPlantCommand(gameSession, inGameRenderer, x, y).execute();
            return true;
        }
        if (InGameRegex.FEED_PLANT.matches(input)) {
            int x = Integer.parseInt(InGameRegex.FEED_PLANT.getGroup(input, "x"));
            int y = Integer.parseInt(InGameRegex.FEED_PLANT.getGroup(input, "y"));
            new FeedPlantCommand(gameSession, inGameRenderer, x, y).execute();
            return true;
        }
        if (InGameRegex.SWAP_PLANTS.matches(input)) {
            int x1 = Integer.parseInt(InGameRegex.SWAP_PLANTS.getGroup(input, "x1"));
            int y1 = Integer.parseInt(InGameRegex.SWAP_PLANTS.getGroup(input, "y1"));
            int x2 = Integer.parseInt(InGameRegex.SWAP_PLANTS.getGroup(input, "x2"));
            int y2 = Integer.parseInt(InGameRegex.SWAP_PLANTS.getGroup(input, "y2"));
            new SwapPlantsCommand(gameSession, inGameRenderer, x1, y1, x2, y2).execute();
            return true;
        }
        if (InGameRegex.UPGRADE_PLANT.matches(input)) {
            String type = InGameRegex.UPGRADE_PLANT.getGroup(input, "type");
            new UpgradePlantsCommand(gameSession, inGameRenderer, type).execute();
            return true;
        }
        return routeMinigamePickups(input);
    }

    // Mini-game specific ways of getting a plant onto the lawn (Vasebreaker vases and seeds, Wall-nut
    // Bowling's conveyor).
    private boolean routeMinigamePickups(String input) {
        if (InGameRegex.BREAK_VASE.matches(input)) {
            int x = Integer.parseInt(InGameRegex.BREAK_VASE.getGroup(input, "x"));
            int y = Integer.parseInt(InGameRegex.BREAK_VASE.getGroup(input, "y"));
            new BreakVaseCommand(gameSession, inGameRenderer, x, y).execute();
            return true;
        }
        if (InGameRegex.COLLECT_SEED.matches(input)) {
            int x = Integer.parseInt(InGameRegex.COLLECT_SEED.getGroup(input, "x"));
            int y = Integer.parseInt(InGameRegex.COLLECT_SEED.getGroup(input, "y"));
            new CollectSeedCommand(gameSession, inGameRenderer, x, y).execute();
            return true;
        }
        if (InGameRegex.BOWL_NUT.matches(input)) {
            String type = InGameRegex.BOWL_NUT.getGroup(input, "type");
            int x = Integer.parseInt(InGameRegex.BOWL_NUT.getGroup(input, "x"));
            int y = Integer.parseInt(InGameRegex.BOWL_NUT.getGroup(input, "y"));
            new BowlNutCommand(gameSession, inGameRenderer, type, x, y).execute();
            return true;
        }
        return false;
    }

    // Putting zombies on the lawn (I, Zombie summons and the spawn cheat), plus the cheats that act on
    // the horde as a whole.
    private boolean routeZombieCommands(String input) {
        if (InGameRegex.SUMMON_ZOMBIE.matches(input)) {
            String type = InGameRegex.SUMMON_ZOMBIE.getGroup(input, "type");
            int x = Integer.parseInt(InGameRegex.SUMMON_ZOMBIE.getGroup(input, "x"));
            int y = Integer.parseInt(InGameRegex.SUMMON_ZOMBIE.getGroup(input, "y"));
            new SummonZombieCommand(gameSession, inGameRenderer, type, x, y).execute();
            return true;
        }
        if (InGameRegex.CHEAT_SPAWN_ZOMBIE.matches(input)) {
            String type = InGameRegex.CHEAT_SPAWN_ZOMBIE.getGroup(input, "type");
            int x = Integer.parseInt(InGameRegex.CHEAT_SPAWN_ZOMBIE.getGroup(input, "x"));
            int y = Integer.parseInt(InGameRegex.CHEAT_SPAWN_ZOMBIE.getGroup(input, "y"));
            new SpawnZombieCheatCommand(gameSession, inGameRenderer, type, x, y).execute();
            return true;
        }
        if (InGameRegex.CHEAT_REMOVE_COOLDOWN.matches(input)) {
            new RemoveCooldownCheatCommand(gameSession, inGameRenderer).execute();
            return true;
        }
        if (InGameRegex.CHEAT_ADD_PLANT_FOOD.matches(input)) {
            new AddPlantFoodCheatCommand(gameSession, inGameRenderer).execute();
            return true;
        }
        if (InGameRegex.RELEASE_THE_NUKE.matches(input)) {
            new ReleaseTheNukeCheatCommand(gameSession, inGameRenderer).execute();
            return true;
        }
        return false;
    }

    // Read-only views of the board. Nothing here changes game state.
    private boolean routeViewCommands(String input) {
        if (InGameRegex.ZOMBIES_INFO.matches(input)) {
            new ZombiesInfoCommand(gameSession, inGameRenderer).execute();
            return true;
        }
        if (InGameRegex.SHOW_MAP.matches(input)) {
            new ShowMapStatusCommand(ShowMapStatusAction.SHOW_MAP, gameSession, mapRenderer, inGameRenderer, 0, 0)
                    .execute();
            return true;
        }
        if (InGameRegex.SHOW_PLANTS_STATUS.matches(input)) {
            new ShowMapStatusCommand(ShowMapStatusAction.SHOW_PLANTS_STATUS, gameSession, mapRenderer,
                    inGameRenderer, 0, 0).execute();
            return true;
        }
        if (InGameRegex.SHOW_TILE_STATUS.matches(input)) {
            int x = Integer.parseInt(InGameRegex.SHOW_TILE_STATUS.getGroup(input, "x"));
            int y = Integer.parseInt(InGameRegex.SHOW_TILE_STATUS.getGroup(input, "y"));
            new ShowMapStatusCommand(ShowMapStatusAction.SHOW_TILE_STATUS, gameSession, mapRenderer, inGameRenderer,
                    x, y).execute();
            return true;
        }
        return false;
    }

    public void advanceTime(int ticks) {
        if (ticks <= 0) {
            inGameRenderer.render(new Result(false, "Time only runs forwards. Give me a positive "
                    + "number of ticks."));
            return;
        }
        for (int i = 0; i < ticks; i++) {
            if (gameSession.getState() != GameState.PLAYING) {
                break;   // the level ended (won/lost) mid-advance; stop simulating further ticks
            }
            advanceOneTick();
        }
    }
}
