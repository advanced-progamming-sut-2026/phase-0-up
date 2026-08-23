package models.user;

import com.google.gson.Gson;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import utils.Constants;
import utils.storage.records.ProfileRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Guards the volume setting's migration, whose failure mode is that every existing player's game opens
// SILENT and nothing anywhere says why.
//
// The other three preferences (gameSpeed, showGrid, debugMode) are plain values, and that is safe for
// them: Gson allocates a Profile without running its constructor, so a field missing from an older save
// keeps Java's zero -- and 0 is not a legal game speed, so getGameSpeed() can read it as "unset" and
// hand back the default. Zero IS a legal volume. It is mute. An `int` here cannot tell a save written
// before this setting existed from a player who deliberately turned the sound off, and it would have
// picked the wrong one for everybody who already has a save file.
//
// Hence Integer, and hence this test: the distinction is invisible in the code and costs nothing to
// break.
class ProfileVolumeTest {

    private static final Gson GSON = new Gson();

    @Test
    @DisplayName("a save written before the setting existed opens at the default, not muted")
    void legacySaveIsNotMuted() {
        // A record with no `volume` key at all, which is exactly what every save on disk looks like.
        ProfileRecord legacy = GSON.fromJson("{\"gameSpeed\":1,\"showGrid\":false}",
                ProfileRecord.class);
        Profile restored = legacy.toProfile();
        assertEquals(Constants.DEFAULT_VOLUME, restored.getVolume());
    }

    @Test
    @DisplayName("a deliberate mute survives the round trip and is not read as 'unset'")
    void muteIsPreserved() {
        Profile profile = new Profile();
        profile.setVolume(Constants.MIN_VOLUME);

        Profile restored = roundTrip(profile);
        assertEquals(Constants.MIN_VOLUME, restored.getVolume(),
            "0 is a choice, not an absent field -- this is the whole reason the field is boxed");
    }

    @Test
    @DisplayName("an ordinary setting survives the round trip")
    void valueIsPreserved() {
        Profile profile = new Profile();
        profile.setVolume(35);
        assertEquals(35, roundTrip(profile).getVolume());
    }

    @Test
    @DisplayName("a fresh profile starts at the default")
    void freshProfileDefaults() {
        assertEquals(Constants.DEFAULT_VOLUME, new Profile().getVolume());
    }

    @Test
    @DisplayName("out-of-range values are clamped rather than stored")
    void clampsOnTheWayIn() {
        Profile profile = new Profile();
        profile.setVolume(500);
        assertEquals(Constants.MAX_VOLUME, profile.getVolume());
        profile.setVolume(-40);
        assertEquals(Constants.MIN_VOLUME, profile.getVolume());
    }

    // Through real Gson and the real record, the same way GreenHouseTest checks its migration: the bug
    // this guards against lives in the serialisation, so a test that skipped it would prove nothing.
    private static Profile roundTrip(Profile profile) {
        String json = GSON.toJson(ProfileRecord.from(profile));
        return GSON.fromJson(json, ProfileRecord.class).toProfile();
    }
}
