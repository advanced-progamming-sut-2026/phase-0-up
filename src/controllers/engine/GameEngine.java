package controllers.engine;

import controllers.commands.ingame.AddSunCheatCommand;
import controllers.commands.ingame.CollectSunCommand;
import controllers.commands.ingame.ShowSunCommand;
import controllers.systems.game.*;
import models.game.GameSession;
import models.game.GameState;
import utils.Result;
import utils.regex.InGameRegex;
import views.InputHandler;
import views.renderers.InGameRenderer;

public class GameEngine {
    private GameSession gameSession;
    private final InGameRenderer inGameRenderer;
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
        this.combatSystem = new CombatSystem();
        this.movementSystem = new MovementSystem();
        this.sunSystem = new SunSystem();
        this.timeSystem = new TimeSystem();
        this.waveSystem = new WaveSystem();
        this.questSystem = new QuestSystem();
    }

    public void startLoop() {
        sunSystem.reset();
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

    public void advanceOneTick() {}


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
