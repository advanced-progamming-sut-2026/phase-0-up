package views.gdx.core;

import java.util.regex.Pattern;

// The sounds the MODEL asks for, off its own narration.
//
// Fifth consumer of `GameScreen.onModelEvent`, alongside the explosions, the weather, the zombie
// actions and the camera shake, and here for the same reason all four of those are: a detonation and a
// death are instants that leave nothing on the board a frame later, so the sentence is the only place
// they exist. It also means a sound arrives on exactly the frame its effect does, because both are
// reading the same line.
//
// The player's own actions are NOT here. Those are cued in CommandBridge, where the command's return
// value says whether anything actually happened -- a click the model refuses has to stay silent.
public final class AudioCues {

    // Same sentences ExplosionEffects, AshEffects and CameraShake match. Deliberately re-stated rather
    // than shared: each consumer owning its own pattern is what lets any of them be changed or dropped
    // without the others noticing, which is the property the fan-out was built for.
    private static final Pattern DETONATION = Pattern.compile("^.+? detonates at \\(\\d+, \\d+\\)!$");
    // The alias is captured, not just matched: a Gargantuar going down should be able to sound
    // different from a Browncoat, and this sentence is the only place the view is told which died.
    private static final Pattern ZOMBIE_DEAD =
            Pattern.compile("^Zombie of type (.+?) is dead at \\(-?\\d+, \\d+\\)$");
    private static final Pattern SMASH =
            Pattern.compile("^.+? smashes .+? to pieces at \\(\\d+, \\d+\\)\\.$");

    private final AudioManager audio;

    public AudioCues(AudioManager audio) {
        this.audio = audio;
    }

    public void onEvent(String message) {
        if (message == null || audio == null) {
            return;
        }
        String text = message.trim();
        if (DETONATION.matcher(text).matches() || SMASH.matcher(text).matches()) {
            audio.play(AudioManager.SFX_EXPLOSION);
            return;
        }
        java.util.regex.Matcher dead = ZOMBIE_DEAD.matcher(text);
        if (dead.matches()) {
            audio.play(AudioManager.forEntity(AudioManager.SFX_ZOMBIE_DIES, dead.group(1).trim()),
                    AudioManager.SFX_ZOMBIE_DIES);
        }
    }

    // ---- state-driven cues -----------------------------------------------------------------------
    //
    // Eating is not an instant and so cannot be an event: a zombie chewing through a Wall-nut is in
    // ActionState.EATING for the better part of a minute, and the model quite rightly says nothing
    // about it after the first tick. What the player should hear is chewing FOR AS LONG AS IT LASTS,
    // which means a repeating cue driven off the board's current state rather than off a sentence.

    // How often a chew is heard while anything is eating. Slower than real chewing on purpose: this is
    // one sound standing in for every zombie on the board, and at a realistic rate a wave of them
    // becomes a drone rather than a warning that something is being eaten.
    private static final float EAT_INTERVAL_SECONDS = 0.55f;

    private float eatTimer;

    // Called once per frame from GameScreen, with the frame's real delta.
    public void tick(models.game.GameSession session, float delta) {
        if (audio == null || session == null || session.getMap() == null) {
            return;
        }
        // One cue for the whole board, not one per zombie. Six zombies eating the same Wall-nut is one
        // sound in the real game, and six overlapping copies of a chew is mush. The alias of the first
        // one found is what picks the sound, so a board where the only thing eating is a Gargantuar can
        // sound like a Gargantuar eating.
        String eater = firstEating(session);
        if (eater == null) {
            // Reset rather than freeze, so the next bite is heard immediately instead of waiting out
            // whatever was left of the previous interval.
            eatTimer = 0f;
            return;
        }
        eatTimer -= delta;
        if (eatTimer <= 0f) {
            eatTimer = EAT_INTERVAL_SECONDS;
            audio.play(AudioManager.forEntity(AudioManager.SFX_ZOMBIE_EAT, eater),
                    AudioManager.SFX_ZOMBIE_EAT);
        }
    }

    // The alias of the first zombie found eating, or null if none is.
    private static String firstEating(models.game.GameSession session) {
        for (models.map.Row row : session.getMap().getRows()) {
            for (models.entities.zombies.Zombie zombie : row.getZombies()) {
                if (zombie.getState().getCurrentAction()
                        == models.entities.zombies.Components.ActionState.EATING) {
                    return zombie.getAlias();
                }
            }
        }
        return null;
    }
}
