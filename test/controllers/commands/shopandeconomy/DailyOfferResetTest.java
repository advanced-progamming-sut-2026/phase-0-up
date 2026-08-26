package controllers.commands.shopandeconomy;

import models.shop.Shop;
import models.user.Profile;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import utils.Result;
import utils.storage.records.ProfileRecord;
import views.renderers.ShopRenderer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// "Today's deal, once a day" -- the rule the shop claimed to have and did not.
//
// Three separate failures met here, and none of them was visible from the store screen, which hid its
// own Buy button after the first press and so looked correct:
//
//   1. Nothing checked whether the deal had already been taken. `shop buy -i 5` could be repeated until
//      the coins ran out, ten packets a time.
//   2. The flag that was supposed to record it lived on Shop's DailyOffer, and Shop is built fresh with
//      every AppSession and never written to the save file. Closing the game forgot the purchase.
//   3. Profile.hasBoughtDailyOfferToday -- which IS persisted, and IS cleared by the LocalDate rollover
//      -- was never set by anything, and read without rolling the day first, so it answered with
//      whatever the last save happened to hold.
//
// Pinned as a test because every one of those is invisible for up to a day: a deal bought twice looks
// fine until tomorrow, and a flag that never clears looks fine until midnight.
class DailyOfferResetTest {

    private static final int DEAL_PRICE = 1600;
    private static final Path DATABASE = Path.of("users_database.json");

    // A successful purchase saves, and DatabaseManager writes users_database.json relative to the
    // working directory -- which the test task pins to the project root, so this would otherwise scribble
    // on the player's real save. Taken before the first getInstance() and put back afterwards.
    private static byte[] savedDatabase;

    @BeforeAll
    static void backUpTheSaveFile() throws IOException {
        savedDatabase = Files.exists(DATABASE) ? Files.readAllBytes(DATABASE) : null;
    }

    @AfterAll
    static void restoreTheSaveFile() throws IOException {
        if (savedDatabase != null) {
            Files.write(DATABASE, savedDatabase);
        }
    }

    // Captures the last thing the command reported, which is the only channel a Command has.
    private static final class Recorder implements ShopRenderer {
        private Result last;

        @Override
        public void listAllProducts(String out) { }

        @Override
        public void listDailyProducts(String out) { }

        @Override
        public void successOfBuyingAProduct(Result result) {
            this.last = result;
        }
    }

    private static Profile shopper() {
        Profile profile = new Profile();
        profile.setCoins(10_000);
        // Pinned to exactly one plant. The deal hands over packets for a RANDOM unlocked plant, and a
        // fresh Profile arrives with the whole starter roster already unlocked and holding one packet
        // each -- so an assertion on a count would have depended on a dice roll, and started from one
        // rather than from zero.
        profile.getUnlockedPlants().clear();
        profile.getUnlockedPlants().add("peashooter");
        profile.getOwnedSeedPackets().clear();
        return profile;
    }

    private static Result buyDeal(Profile profile, Recorder renderer) {
        new BuyShopItemCommand(5, new Shop(), 1, null, profile, renderer).execute();
        return renderer.last;
    }

    @Test
    void theFirstPurchaseOfTheDayGoesThrough() {
        Profile profile = shopper();
        Recorder renderer = new Recorder();

        Result result = buyDeal(profile, renderer);

        assertTrue(result.success(), result.message());
        assertEquals(10_000 - DEAL_PRICE, profile.getCoins());
        assertEquals(10, profile.getOwnedSeedPackets().get("peashooter"));
        assertTrue(profile.isHasBoughtDailyOfferToday());
    }

    @Test
    void theSecondPurchaseOnTheSameDayIsRefusedAndCostsNothing() {
        Profile profile = shopper();
        Recorder renderer = new Recorder();
        buyDeal(profile, renderer);
        int afterTheFirst = profile.getCoins();

        Result second = buyDeal(profile, renderer);

        assertFalse(second.success());
        assertTrue(second.message().toLowerCase().contains("already"), second.message());
        assertEquals(afterTheFirst, profile.getCoins());
        // Ten packets, not twenty: the refusal has to come before the goods, not merely before the save.
        assertEquals(10, profile.getOwnedSeedPackets().get("peashooter"));
    }

    // The half that a restart used to lose.
    @Test
    void thePurchaseSurvivesASaveAndReloadOnTheSameDay() {
        Profile profile = shopper();
        buyDeal(profile, new Recorder());

        Profile reloaded = ProfileRecord.from(profile).toProfile();

        assertTrue(reloaded.isHasBoughtDailyOfferToday());
    }

    // The half that a restart used to fake: the deal came back because the object was rebuilt, not
    // because a day had passed. Now the day is what does it.
    @Test
    void aStaleDayStampClearsItOnTheNextRead() {
        Profile profile = shopper();
        buyDeal(profile, new Recorder());
        profile.setQuestDayStamp(LocalDate.now().minusDays(1).toString());

        assertFalse(profile.isHasBoughtDailyOfferToday());
        // And the roll is recorded, so it does not keep re-clearing a purchase made after midnight.
        assertEquals(LocalDate.now().toString(), profile.getQuestDayStamp());
    }

    @Test
    void aSaveFromYesterdayOpensWithTheDealAvailableAgain() {
        Profile profile = shopper();
        buyDeal(profile, new Recorder());
        profile.setQuestDayStamp(LocalDate.now().minusDays(1).toString());

        // from() reads the raw flag, so yesterday's "true" is what actually goes into the file...
        ProfileRecord record = ProfileRecord.from(profile);
        Profile reopened = record.toProfile();

        // ...and the day stamp it travels with is what makes tomorrow's first read clear it.
        assertFalse(reopened.isHasBoughtDailyOfferToday());
    }

    // Buying again the day after is the whole point, and it is the one path that needed all three fixes
    // at once.
    @Test
    void tomorrowTheDealCanBeBoughtAgain() {
        Profile profile = shopper();
        Recorder renderer = new Recorder();
        buyDeal(profile, renderer);
        profile.setQuestDayStamp(LocalDate.now().minusDays(1).toString());

        Result nextDay = buyDeal(profile, renderer);

        assertTrue(nextDay.success(), nextDay.message());
        assertEquals(10_000 - DEAL_PRICE * 2, profile.getCoins());
        assertEquals(20, profile.getOwnedSeedPackets().get("peashooter"));
    }

    // The terminal listing has to say the same thing the store screen shows.
    @Test
    void theTerminalListingReportsWhatTheProfileHolds() {
        Shop shop = new Shop();

        assertTrue(shop.showDailyOffer(false).contains("Is purchased : false"));
        assertTrue(shop.showDailyOffer(true).contains("Is purchased : true"));
    }
}
