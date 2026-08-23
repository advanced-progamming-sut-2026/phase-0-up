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
    private static final Pattern ZOMBIE_DEAD =
            Pattern.compile("^Zombie of type .+? is dead at \\(-?\\d+, \\d+\\)$");
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
        if (ZOMBIE_DEAD.matcher(text).matches()) {
            audio.play(AudioManager.SFX_ZOMBIE_DIES);
        }
    }
}
