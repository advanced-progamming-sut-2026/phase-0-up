package controllers.engine;

import controllers.systems.CombatSystem;
import controllers.systems.SunSystem;
import controllers.systems.TimeSystem;
import controllers.systems.WaveSystem;
import models.game.GameSession;

public class GameEngine {
    private GameSession gameSession;
    private CombatSystem combatSystem;
    private SunSystem sunSystem;
    private TimeSystem timeSystem;
    private WaveSystem waveSystem;
    private boolean running;

    public void startLoop(GameSession gameSession) {}
    public void stopLoop() {}
    private void run(){} //main method which updates every entity in game on every tick
}
