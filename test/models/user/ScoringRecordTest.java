package models.user;

import models.leaderboard.LbColumn;
import models.leaderboard.LeaderboardEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import utils.storage.records.ProfileRecord;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// "Never played" is not "scored zero".
//
// The spec draws that line explicitly -- a player who has not played the networked scoring game must
// not show a previous or fake score in the leaderboard's "My Point" column -- and an int cannot draw
// it, because 0 is a score a real run can genuinely end on. This is the test that keeps the
// distinction from being quietly unboxed away again, which is exactly what would happen the first time
// someone "simplified" the null checks.
class ScoringRecordTest {

    @Test
    @DisplayName("a fresh profile has never played, which is not the same as having scored zero")
    void freshProfileHasNoRecord() {
        Profile profile = new Profile();
        assertNull(profile.getBestNumberOfMeowPoints());
        assertFalse(profile.hasScoringGameRecord());
    }

    @Test
    @DisplayName("the first run is always a record, even when it scores zero")
    void firstRunIsAlwaysARecord() {
        Profile profile = new Profile();
        // Zero is a legal outcome: no kills, no leftover sun. It still has to REGISTER, or the player
        // stays permanently "never played" no matter how many runs they finish.
        assertTrue(profile.recordScoringGameRun(0));
        assertTrue(profile.hasScoringGameRecord());
        assertEquals(0, profile.getBestNumberOfMeowPoints());
    }

    @Test
    @DisplayName("only a strictly higher score replaces the record")
    void onlyABetterScoreCounts() {
        Profile profile = new Profile();
        assertTrue(profile.recordScoringGameRun(500));

        assertFalse(profile.recordScoringGameRun(499), "a worse run must not overwrite the best");
        assertFalse(profile.recordScoringGameRun(500), "matching the best is not beating it");
        assertEquals(500, profile.getBestNumberOfMeowPoints());

        assertTrue(profile.recordScoringGameRun(501));
        assertEquals(501, profile.getBestNumberOfMeowPoints());
    }

    // ---- persistence ----------------------------------------------------------------------------

    @Test
    @DisplayName("never-played survives a save and reload as never-played")
    void neverPlayedSurvivesPersistence() {
        Profile saved = ProfileRecord.from(new Profile()).toProfile();
        assertNull(saved.getBestNumberOfMeowPoints(),
                "a round trip must not invent a score for somebody who has not played");
    }

    @Test
    @DisplayName("a real score survives a save and reload")
    void realScoreSurvivesPersistence() {
        Profile profile = new Profile();
        profile.recordScoringGameRun(1234);

        Profile reloaded = ProfileRecord.from(profile).toProfile();
        assertEquals(1234, reloaded.getBestNumberOfMeowPoints());
    }

    @Test
    @DisplayName("a legacy save's stored 0 loads as never-played, not as a score of zero")
    void legacyZeroIsMigratedToNeverPlayed() {
        // Every save written before this field was boxed carries a literal 0 for a player who never
        // touched the scoring game -- the old default, which nobody earned. Loading it as a real score
        // would put a fake 0 in the "My Point" column of every legacy account.
        Profile profile = new Profile();
        profile.setBestNumberOfMeowPoints(0);
        ProfileRecord legacy = ProfileRecord.from(profile);

        assertNull(legacy.toProfile().getBestNumberOfMeowPoints());
    }

    @Test
    @DisplayName("the multiplayer record persists")
    void versusRecordSurvivesPersistence() {
        Profile profile = new Profile();
        profile.recordVersusResult(true);
        profile.recordVersusResult(true);
        profile.recordVersusResult(false);

        Profile reloaded = ProfileRecord.from(profile).toProfile();
        assertEquals(2, reloaded.getVersusWins());
        assertEquals(1, reloaded.getVersusLosses());
    }

    // ---- the leaderboard ------------------------------------------------------------------------

    @Test
    @DisplayName("a never-played score renders as a dash, never as 0 and never as \"null\"")
    void neverPlayedRendersAsADash() {
        assertEquals("-", entry("nobody", null).getMeowPointLabel());
        assertEquals("0", entry("zero", 0).getMeowPointLabel());
        assertEquals("900", entry("ace", 900).getMeowPointLabel());
    }

    @Test
    @DisplayName("sorting by score does not throw when nobody has played yet")
    void sortingSurvivesAnAllNullBoard() {
        // On a fresh server this is EVERY row. A comparingInt on the boxed getter would unbox null and
        // take the whole leaderboard screen down on its first open.
        List<LeaderboardEntry> rows = new ArrayList<>(List.of(
                entry("bea", null), entry("amir", null), entry("cy", null)));
        rows.sort(LbColumn.MEOW_POINT.ascendingComparator());

        // Falls through to the username tie-break, exactly as equal real scores do.
        assertEquals(List.of("amir", "bea", "cy"), names(rows));
    }

    @Test
    @DisplayName("players who have never played rank below every real score, zero included")
    void neverPlayedRanksLast() {
        List<LeaderboardEntry> rows = new ArrayList<>(List.of(
                entry("ace", 900), entry("nobody", null), entry("zero", 0)));

        // Descending is how the board opens (InputRouter's DEFAULT_LB_ASCENDING is false), so this is
        // the ordering players actually see: real scores first, and "has not played" at the bottom --
        // below a genuine zero, because not playing is not an achievement.
        rows.sort(LbColumn.MEOW_POINT.ascendingComparator().reversed());
        assertEquals(List.of("ace", "zero", "nobody"), names(rows));

        rows.sort(LbColumn.MEOW_POINT.ascendingComparator());
        assertEquals(List.of("nobody", "zero", "ace"), names(rows));
    }

    private static LeaderboardEntry entry(String username, Integer meowPoints) {
        return new LeaderboardEntry(username, 1, 1, 0, 0, 0, meowPoints);
    }

    private static List<String> names(List<LeaderboardEntry> rows) {
        return rows.stream().map(LeaderboardEntry::getUsername).toList();
    }
}
