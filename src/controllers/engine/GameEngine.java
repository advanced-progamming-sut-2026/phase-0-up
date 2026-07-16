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
    private MovementSystem movementSystem;
    private SunSystem sunSystem;
    private TimeSystem timeSystem;
    private WaveSystem waveSystem;
    private QuestSystem questSystem;
    private boolean running;

    public GameEngine(GameSession gameSession) {
        this.gameSession = gameSession;
        this.inGameRenderer = new InGameRenderer();
        this.mapRenderer = new MapRenderer();
        this.combatSystem = new CombatSystem();
        this.movementSystem = new MovementSystem();
        this.sunSystem = new SunSystem();
        this.timeSystem = new TimeSystem();
        this.waveSystem = new WaveSystem();
        this.questSystem = new QuestSystem();
    }

    public void startLoop() {
        sunSystem.reset();
        gameSession.startMode();
        running = true;
        run();
    }
    public void stopLoop() {running = false;}

    private void run() {
        while (running && gameSession.getState() == GameState.PLAYING) {
            String input = InputHandler.readLine().trim();
            if (input.isEmpty()) {
                continue;
            }
            if (!routeAndExecute(input)) {
                inGameRenderer.render(new Result(false, "Invalid command."));
            }
        }
    }

    public void advanceOneTick() {
        // TimeSystem.advance drives the clock and per-tick systems (owned separately); once state has
        // settled for this tick, evaluate the level's win/lose rules. Kept here, not in TimeSystem,
        // so time-advancement and rule-evaluation never interfere.
        gameSession.evaluateModeRules();
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
            advanceOneTick();
        }
    }
}
