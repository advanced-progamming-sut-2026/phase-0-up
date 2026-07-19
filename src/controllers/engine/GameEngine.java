package controllers.engine;

import controllers.commands.ingame.*;
import controllers.systems.game.*;
import models.game.GameSession;
import models.game.GameState;
import utils.Result;
import utils.regex.InGameRegex;
import views.InputHandler;
import views.renderers.InGameRenderer;
import views.renderers.MapRenderer;

public class GameEngine {
    private GameSession gameSession;
    private final InGameRenderer inGameRenderer;
    private final MapRenderer mapRenderer;
    private CombatSystem combatSystem;
    private SunSystem sunSystem;
    private TimeSystem timeSystem;
    private WaveSystem waveSystem;
    private QuestSystem questSystem;
    private EnvironmentSystem environmentSystem;
    private boolean running;

    public GameEngine(GameSession gameSession) {
        this.gameSession = gameSession;
        this.inGameRenderer = new InGameRenderer();
        this.mapRenderer = new MapRenderer();
        this.combatSystem = new CombatSystem();
        this.sunSystem = new SunSystem();
        this.timeSystem = new TimeSystem();
        this.waveSystem = new WaveSystem();
        this.questSystem = new QuestSystem();
        this.environmentSystem = new EnvironmentSystem();
        this.combatSystem.setQuestSystem(questSystem);   // combat reports kills/losses to the quest tally
    }

    public void startLoop() {
        sunSystem.reset();
        gameSession.startMode();
        gameSession.applySeedBoosts();   // carry seed-selection boosts into the live seed packets
        questSystem.startTrackingLevel(gameSession);
        running = true;
        run();
    }
    public void stopLoop() {running = false;}

    private void run() {
        while (running && gameSession.getState() == GameState.PLAYING) {
            String input = InputHandler.readLine();
            if (input == null) {   // stdin closed (EOF) -> leave the game loop instead of spinning
                running = false;
                break;
            }
            if (input.isEmpty()) {
                continue;
            }
            if (!routeAndExecute(input)) {
                inGameRenderer.render(new Result(false, "Invalid command."));
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
        // Terrain reacts after the entities have moved, so it sees where they actually ended up
        // (a zombie that just stepped onto a slider tile, ice that a fire plant is now beside, ...).
        environmentSystem.tick(gameSession);

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
        if (after == GameState.WON) {
            inGameRenderer.render(new Result(true,
                    "Dear humanz, zis is not done yet; we will come back to eat your brainz, humanz."));
            // The level is won: evaluate quests against it and announce any that just completed (their
            // rewards are granted straight to the profile).
            for (Result quest : questSystem.evaluateAndComplete(gameSession.getPlayer(), gameSession, true)) {
                inGameRenderer.render(quest);
            }
        } else if (after == GameState.LOST) {
            inGameRenderer.render(new Result(false, "The zombie ate your brain; LOSER!!!"));
            // A loss still ends a level: quests are evaluated so the cross-level counters settle (the
            // max-difficulty win streak breaks) and any quest that doesn't need a win -- chapter kills,
            // the mowerless last-stand kills -- can still complete on what happened this level.
            for (Result quest : questSystem.evaluateAndComplete(gameSession.getPlayer(), gameSession, false)) {
                inGameRenderer.render(quest);
            }
        }
    }


    private boolean routeAndExecute(String input) {
        if (InGameRegex.COLLECT_SUN.matches(input)) {
            int x = Integer.parseInt(InGameRegex.COLLECT_SUN.getGroup(input, "x"));
            int y = Integer.parseInt(InGameRegex.COLLECT_SUN.getGroup(input, "y"));
            new CollectSunCommand(gameSession, sunSystem, inGameRenderer,questSystem , x, y).execute();
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
        if (InGameRegex.CHEAT_REMOVE_COOLDOWN.matches(input)) {
            new RemoveCooldownCheatCommand(gameSession, inGameRenderer).execute();
            return true;
        }
        if (InGameRegex.RELEASE_THE_NUKE.matches(input)) {
            new ReleaseTheNukeCheatCommand(gameSession, inGameRenderer).execute();
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
            inGameRenderer.render(new Result(false, "Tick count must be positive."));
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
