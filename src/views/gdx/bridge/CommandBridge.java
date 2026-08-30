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

    // Optional, and null in every test: CommandBridgeTest builds this with a collecting sink and no
    // game around it. A cue is played only when the command actually SUCCEEDED, which is the whole
    // reason the sounds live here rather than in the input processor -- a click on a tile the player
    // cannot afford should be silent, or the sound becomes a lie about what happened.
    private views.gdx.core.AudioManager audio;

    public CommandBridge(CommandSink sink) {
        this.sink = sink;
    }

    public void setAudio(views.gdx.core.AudioManager audio) {
        this.audio = audio;
    }

    // `sfx` is a fallback chain: the first name there is a file for wins. See AudioManager.play.
    private boolean cue(boolean happened, String... sfx) {
        if (happened && audio != null) {
            audio.play(sfx);
        }
        return happened;
    }

    // "plant plant -t <type> -l (x, y)". The type is passed through untouched: PlantRegistry matches
    // it ignoring case, so whatever the seed card is labelled with works.
    public boolean plant(String plantType, GridPos at) {
        if (plantType == null || plantType.isBlank() || !at.isValid()) {
            return false;
        }
        // The plant's own placement sound if there is a file for it, otherwise the generic one.
        return cue(submit("plant plant -t " + plantType.trim() + " -l " + at.toCommandArgs()),
                views.gdx.core.AudioManager.forEntity(
                        views.gdx.core.AudioManager.SFX_PLANT, plantType),
                views.gdx.core.AudioManager.SFX_PLANT);
    }

    // "pluck plant -l (x, y)" -- the shovel.
    public boolean pluck(GridPos at) {
        return cue(at.isValid() && submit("pluck plant -l " + at.toCommandArgs()),
                views.gdx.core.AudioManager.SFX_SHOVEL);
    }

    // "feed plant -l (x, y)" -- plant food.
    public boolean feed(GridPos at) {
        return at.isValid() && submit("feed plant -l " + at.toCommandArgs());
    }

    // "collect sun -l (x, y)". Note this takes the sun's TILE, not its pixel position: the model
    // locates a sun by flooring its x to a column and comparing rows, so the tile under the cursor is
    // exactly the right thing to send.
    public boolean collectSun(GridPos at) {
        return cue(at.isValid() && submit("collect sun -l " + at.toCommandArgs()),
                views.gdx.core.AudioManager.SFX_SUN_COLLECT);
    }

    // "collect plant-food -l (x, y)" -- the pickup a glowing zombie left on the lawn. Same shape as
    // collectSun above, and it borrows the sun's pickup cue: there is no plant-food sound in the
    // dump, and a collect that makes no noise at all reads as a click that did not register.
    public boolean collectPlantFood(GridPos at) {
        return cue(at.isValid() && submit("collect plant-food -l " + at.toCommandArgs()),
                views.gdx.core.AudioManager.SFX_SUN_COLLECT);
    }

    // "break vase -l (x, y)" -- Vasebreaker. A bare click on a vase, with nothing held.
    public boolean breakVase(GridPos at) {
        return at.isValid() && submit("break vase -l " + at.toCommandArgs());
    }

    // "collect seed -l (x, y)" -- picking up what a vase dropped.
    public boolean collectSeed(GridPos at) {
        return at.isValid() && submit("collect seed -l " + at.toCommandArgs());
    }

    // "bowl -t <kind> -l (x, y)" -- Wall-nut Bowling. The kind is the mode's own token; the red-line
    // rule and the "is that nut on the belt" check both stay in WallnutBowlingMode.bowlNut.
    public boolean bowl(String kindToken, GridPos at) {
        if (kindToken == null || kindToken.isBlank() || !at.isValid()) {
            return false;
        }
        return submit("bowl -t " + kindToken.trim() + " -l " + at.toCommandArgs());
    }

    // "summon -t <alias> -l (x, y)" -- I, Zombie. The price, the roster check and the red-line rule are
    // all IZombieMode.summonZombie's; this only names the zombie and the tile.
    public boolean summon(String zombieAlias, GridPos at) {
        if (zombieAlias == null || zombieAlias.isBlank() || !at.isValid()) {
            return false;
        }
        return submit("summon -t " + zombieAlias.trim() + " -l " + at.toCommandArgs());
    }

    // "swap -l (x1, y1) (x2, y2)" -- Beghouled's only gesture.
    //
    // ## The coordinate trap, flagged in the roadmap since Phase 0
    //
    // Three orderings meet at this command and two of them disagree:
    //
    //   the command   swap -l (x1, y1) (x2, y2)            x FIRST
    //   GameSession   swapPlants(x1, y1, x2, y2)           x FIRST
    //   BeghouledMode swap(session, r1, c1, r2, c2)        ROW FIRST
    //
    // `GameSession.swapPlants` already transposes for the mode (`swap(this, y1, x1, y2, x2)`), so the
    // transposition is done and this side must NOT do it again. GridPos is (col, row) and
    // `toCommandArgs` prints it that way, which is exactly what the pattern wants -- so the safe
    // spelling is the one that never mentions x or y at all.
    //
    // Getting it wrong does not throw: it silently swaps the transposed cell, and on a board that is
    // nearly square that usually still looks plausible. Which is the worst kind of wrong, and why
    // `CommandBridgeTest` asserts the exact string.
    public boolean swap(GridPos from, GridPos to) {
        if (from == null || to == null || !from.isValid() || !to.isValid()) {
            return false;
        }
        return cue(submit("swap -l " + from.toCommandArgs() + " " + to.toCommandArgs()),
                views.gdx.core.AudioManager.SFX_PLANT);
    }

    // "upgrade -t <type>" -- Beghouled's sun sink. The affordability check, the "not one of those on the
    // lawn" refusal and the actual promotion all stay in BeghouledMode.upgrade.
    public boolean upgrade(String plantType) {
        if (plantType == null || plantType.isBlank()) {
            return false;
        }
        return cue(submit("upgrade -t " + plantType.trim()),
                views.gdx.core.AudioManager.forEntity(
                        views.gdx.core.AudioManager.SFX_PLANT, plantType),
                views.gdx.core.AudioManager.SFX_PLANT);
    }

    private boolean submit(String command) {
        return sink.submit(command);
    }
}
