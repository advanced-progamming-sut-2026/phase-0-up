package views.gdx.bridge;

import views.gdx.map.GridPos;

// Turns a gesture into the command string the game already understands, and runs it.
//
// This is the third of the phase-2 bridges, and the one that keeps the two front ends honest. Clicking
// a tile does NOT call gameSession.plant() -- it builds "plant plant -t Sunflower -l (0, 2)" and posts
// it through GameEngine, the same door the keyboard uses. Everything downstream (the cost check, the
// seed cooldown, "there is already a plant there", the aquatic rule, the quest tally, the event that
// becomes a toast) runs once and is written once.
//
// The alternative -- calling the model directly from the input handler -- would work on the first day
// and then quietly diverge, because every rule added to a Command afterwards would have to be
// remembered here too. Synthesising the string costs a few microseconds per click and makes that
// impossible.
public final class CommandBridge {

    // Where a synthesised command goes. In the game this is GameEngine::submitInGameCommand; a test
    // passes a collector instead, which is the only practical way to assert that the strings built
    // here actually match the patterns the engine dispatches on. A mismatched format does not throw --
    // it silently matches nothing, and the click appears to do nothing at all.
    @FunctionalInterface
    public interface CommandSink {
        boolean submit(String command);
    }

    private final CommandSink sink;

    public CommandBridge(CommandSink sink) {
        this.sink = sink;
    }

    // "plant plant -t <type> -l (x, y)". The type is passed through untouched: PlantRegistry matches
    // it ignoring case, so whatever the seed card is labelled with works.
    public boolean plant(String plantType, GridPos at) {
        if (plantType == null || plantType.isBlank() || !at.isValid()) {
            return false;
        }
        return submit("plant plant -t " + plantType.trim() + " -l " + at.toCommandArgs());
    }

    // "pluck plant -l (x, y)" -- the shovel.
    public boolean pluck(GridPos at) {
        return at.isValid() && submit("pluck plant -l " + at.toCommandArgs());
    }

    // "feed plant -l (x, y)" -- plant food.
    public boolean feed(GridPos at) {
        return at.isValid() && submit("feed plant -l " + at.toCommandArgs());
    }

    // "collect sun -l (x, y)". Note this takes the sun's TILE, not its pixel position: the model
    // locates a sun by flooring its x to a column and comparing rows, so the tile under the cursor is
    // exactly the right thing to send.
    public boolean collectSun(GridPos at) {
        return at.isValid() && submit("collect sun -l " + at.toCommandArgs());
    }

    private boolean submit(String command) {
        return sink.submit(command);
    }
}
