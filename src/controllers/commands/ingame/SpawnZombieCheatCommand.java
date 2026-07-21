package controllers.commands.ingame;

import controllers.commands.Command;
import models.game.GameSession;
import views.renderers.InGameRenderer;

// "cheat spawn-zombie -t <zombie-type> -l (x, y)": a debug cheat that drops a zombie of the given type
// onto column x of row y, on any level. All the placement/validation lives on the GameSession; this
// command just wires the parsed arguments to it and renders the outcome.
public class SpawnZombieCheatCommand implements Command {
    private final GameSession gameSession;
    private final InGameRenderer renderer;
    private final String zombieType;
    private final int column;
    private final int row;

    public SpawnZombieCheatCommand(GameSession gameSession, InGameRenderer renderer, String zombieType,
                                   int column, int row) {
        this.gameSession = gameSession;
        this.renderer = renderer;
        this.zombieType = zombieType;
        this.column = column;
        this.row = row;
    }

    @Override
    public void execute() {
        renderer.render(gameSession.spawnZombieCheat(zombieType, column, row));
    }
}
