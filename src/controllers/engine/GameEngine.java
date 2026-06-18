package controllers.engine;

import controllers.systems.CombatSystem;
import controllers.systems.SunSystem;
import controllers.systems.TimeSystem;
import controllers.systems.WaveSystem;
import models.game.GameSession;
import views.OutputHandler;

public class GameEngine {
    private GameSession gameSession;
    private CombatSystem combatSystem;
    private SunSystem sunSystem;
    private TimeSystem timeSystem;
    private WaveSystem waveSystem;
    private boolean running;

    public void startLoop() {}
    public void stopLoop() {}

    public void advanceOneTick() {

    }
}
