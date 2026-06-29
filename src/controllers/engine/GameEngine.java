package controllers.engine;

import controllers.systems.game.*;
import models.game.GameSession;

public class GameEngine {
    private GameSession gameSession;
    private CombatSystem combatSystem;
    private MovementSystem movementSystem;
    private SunSystem sunSystem;
    private TimeSystem timeSystem;
    private WaveSystem waveSystem;
    private QuestSystem questSystem;
    private boolean running;

    public void startLoop() {}
    public void stopLoop() {}

    public void advanceOneTick() {

    }
}
