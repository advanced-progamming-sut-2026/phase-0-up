package views.gdx.core;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Disposable;
import utils.Constants;

import java.util.HashMap;
import java.util.Map;

// Music and sound effects, and the volume that governs them.
//
// ## Read this before wiring anything to it
//
// **`pvz-assets/` contains no audio at all.** The dump is `ATLASES/`, `Exports/`, `IMAGES/`,
// `RESOURCES.json`, `animations.json` and `browser.jar` -- not one `.ogg`, `.mp3` or `.wav` anywhere in
// it or in the repo. So the spec's "background tracks + SFX" cannot be met from shipped assets, and the
// project has a standing rule against inventing or downloading art to fill a gap like this one.
//
// This class is therefore the complete seam with nothing to play through it yet: every call site is
// wired, the volume is a real saved setting with a real control, and **the moment audio files are
// dropped into `assets/audio/` the game has sound with no further code change**. Nothing here throws,
// logs repeatedly, or costs anything when the folder is absent -- a missing clip is reported once and
// then permanently ignored.
//
// ## Where files go
//
//     assets/audio/music/<name>.ogg      looped background tracks
//     assets/audio/sfx/<name>.ogg        one-shot effects
//
// The names the game asks for are the constants below. `.ogg` is tried first and `.mp3` second, because
// LibGDX's LWJGL3 backend supports both and OGG loops without the leading silence MP3 encoders add.
public final class AudioManager implements Disposable {

    // The root, relative to the working directory -- which build.gradle pins to rootDir for both entry
    // points, the same way data/*.json and users_database.json are resolved.
    private static final String ROOT = "assets/audio";

    private static final String[] EXTENSIONS = {".ogg", ".mp3"};

    // ---- the names the game asks for -------------------------------------------------------------
    //
    // Every one of these is already called from somewhere. They are the contract: supply a file with a
    // matching name and that moment in the game has sound.

    /** Looped, while a level is being played. The generic fallback under every per-world track. */
    public static final String MUSIC_LAWN = "lawn";
    /** Looped, on the menus. The generic fallback under every per-screen track. */
    public static final String MUSIC_MENU = "menu";
    // No MUSIC_TITLE: this build has no splash screen -- LoginScreen is the entry point and it is an
    // ordinary MenuScreen -- so a title track would be a name nobody could ever supply a file for.
    /** The greenhouse, which is this game's Zen Garden. */
    public static final String MUSIC_GREENHOUSE = "greenhouse";
    /** Seed selection, before a level starts. */
    public static final String MUSIC_SEED_SELECT = "seedselect";
    /** The mini-games, which have no world of their own. */
    public static final String MUSIC_MINIGAME = "minigame";

    // The lawn track for a world, and for how far into the level it is.
    //
    // Split because the soundtrack is: every world ships a "First Wave" theme and a separate, more
    // urgent "Final Wave" one, and the whole point of the second is that the player hears the level
    // turn. Callers pass both this and the plainer fallbacks to playMusic, so supplying only
    // `lawn_egypt` -- or only `lawn` -- still gives the game music.
    public static String lawnTrack(models.game.EnvironmentType environment, boolean finalWave) {
        return MUSIC_LAWN + "_" + worldKey(environment) + (finalWave ? "_final" : "");
    }

    // Readable names matching the soundtrack's own world titles, rather than the atlas keys
    // BackgroundRenderer uses ("ICEAGE", "DARK"). These names are what a person has to type when they
    // name a file, so they are chosen to be guessable rather than consistent with the art pipeline.
    private static String worldKey(models.game.EnvironmentType environment) {
        if (environment == null) {
            return "egypt";
        }
        return switch (environment) {
            case ANCIENT_EGYPT -> "egypt";
            case FROSTBITE_CAVES -> "frostbite";
            case BIG_WAVE_BEACH -> "beach";
            case DARK_AGES -> "dark";
        };
    }

    public static final String SFX_PLANT = "plant";
    public static final String SFX_SHOOT = "shoot";
    public static final String SFX_ZOMBIE_EAT = "zombie_eat";
    public static final String SFX_ZOMBIE_DIES = "zombie_dies";
    public static final String SFX_EXPLOSION = "explosion";
    public static final String SFX_SUN_COLLECT = "sun_collect";
    public static final String SFX_SHOVEL = "shovel";
    public static final String SFX_BUTTON = "button";
    public static final String SFX_LOSE = "lose";
    public static final String SFX_WIN = "win";

    // Effects are capped below the music so a wave of them cannot drown the track. Both are multiplied
    // by the master volume, which is the only thing the player controls.
    private static final float SFX_MIX = 0.85f;
    private static final float MUSIC_MIX = 0.55f;

    private final Map<String, Sound> sounds = new HashMap<>();
    private final Map<String, Music> tracks = new HashMap<>();
    // Names already found to be missing. Without this, a shot that fires ten times a second would try
    // to open a file ten times a second and log ten times a second.
    private final java.util.Set<String> absent = new java.util.HashSet<>();

    private Music current;
    private String currentName;
    private float master = Constants.DEFAULT_VOLUME / 100f;

    // 0-100, straight off the Profile. Applied immediately, so dragging the slider is audible while it
    // is being dragged rather than after the screen is left.
    public void setVolume(int percent) {
        master = Math.max(0, Math.min(Constants.MAX_VOLUME, percent)) / 100f;
        if (current != null) {
            current.setVolume(master * MUSIC_MIX);
        }
    }

    public int getVolume() {
        return Math.round(master * 100f);
    }

    // A one-shot effect. Silently does nothing when there is no file for it.
    public void play(String name) {
        Sound sound = sound(name);
        if (sound != null && master > 0f) {
            sound.play(master * SFX_MIX);
        }
    }

    // Starts a looping track: the first of these names there is actually a file for.
    //
    // The fallback chain is what keeps the audio folder optional at every level of detail. A caller asks
    // for `lawn_egypt_final`, then `lawn_egypt`, then `lawn` -- so supplying thirteen files gives every
    // world its own theme and a separate one for the closing wave, supplying four gives each world a
    // theme, and supplying ONE called `lawn.ogg` gives the whole game music. Nobody has to provide a
    // full set to hear anything.
    //
    // Does nothing when the winning name is already the one playing, so this is safe to call every
    // frame -- which is how the switch to the final-wave track happens without anything watching for it.
    public void playMusic(String... names) {
        if (names == null || names.length == 0) {
            return;
        }
        for (String name : names) {
            if (name == null) {
                continue;
            }
            if (name.equals(currentName)) {
                return;      // already playing this one
            }
            Music music = music(name);
            if (music != null) {
                stopMusic();
                currentName = name;
                current = music;
                music.setLooping(true);
                music.setVolume(master * MUSIC_MIX);
                music.play();
                return;
            }
        }
        // Nothing in the chain exists. Remembered as the last candidate so the whole chain is not
        // re-probed on the next frame; music() caches the misses too.
        currentName = names[names.length - 1];
    }

    public void stopMusic() {
        if (current != null) {
            current.stop();
        }
        current = null;
        currentName = null;
    }

    private Sound sound(String name) {
        if (sounds.containsKey(name)) {
            return sounds.get(name);
        }
        FileHandle file = resolve("sfx", name);
        Sound sound = null;
        if (file != null) {
            try {
                sound = Gdx.audio.newSound(file);
            } catch (RuntimeException e) {
                // A broken or unsupported file must not take the frame down; the game simply stays
                // quiet for that one effect.
                Gdx.app.error("AudioManager", "could not load sfx " + file.path(), e);
            }
        }
        sounds.put(name, sound);
        return sound;
    }

    private Music music(String name) {
        if (tracks.containsKey(name)) {
            return tracks.get(name);
        }
        FileHandle file = resolve("music", name);
        Music music = null;
        if (file != null) {
            try {
                music = Gdx.audio.newMusic(file);
            } catch (RuntimeException e) {
                Gdx.app.error("AudioManager", "could not load music " + file.path(), e);
            }
        }
        tracks.put(name, music);
        return music;
    }

    // The first existing file for this name, or null. Reported exactly once per name.
    private FileHandle resolve(String folder, String name) {
        for (String extension : EXTENSIONS) {
            FileHandle file = Gdx.files.local(ROOT + "/" + folder + "/" + name + extension);
            if (file.exists()) {
                return file;
            }
        }
        if (absent.add(folder + "/" + name)) {
            Gdx.app.log("AudioManager", "no audio for " + folder + "/" + name
                    + " -- drop " + ROOT + "/" + folder + "/" + name + ".ogg in to give it a sound");
        }
        return null;
    }

    // Owned by PvZGame, created once and disposed once, like everything else that holds a native
    // handle. Sounds and Music are separate LibGDX resources and neither is released by the other.
    @Override
    public void dispose() {
        stopMusic();
        for (Sound sound : sounds.values()) {
            if (sound != null) {
                sound.dispose();
            }
        }
        for (Music music : tracks.values()) {
            if (music != null) {
                music.dispose();
            }
        }
        sounds.clear();
        tracks.clear();
    }
}
