package utils.regex;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Guards the strings StoreScreen synthesises against the pattern the router matches them with.
//
// Same seam as CommandBridgeTest, same failure mode: a purchase that does not match is not an error,
// it falls through to "invalid command" -- so a plant name the pattern cannot express makes a whole
// shop item silently unbuyable, in the terminal as well as the GUI. That is exactly what -t's original
// \S+ did to every plant with a space in its name.
class ShopMenuRegexTest {

    @Test
    @DisplayName("a purchase without a plant type parses")
    void plainPurchase() {
        String command = "shop buy -i 1 -n 3";
        assertTrue(ShopMenuRegex.BUY.matches(command), command);
        assertEquals("1", ShopMenuRegex.BUY.getGroup(command, "id"));
        assertEquals("3", ShopMenuRegex.BUY.getGroup(command, "number"));
        assertEquals(null, ShopMenuRegex.BUY.getGroup(command, "plantType"));
    }

    @Test
    @DisplayName("a one-word plant type parses")
    void singleWordPlant() {
        String command = "shop buy -i 3 -n 1 -t Peashooter";
        assertTrue(ShopMenuRegex.BUY.matches(command), command);
        assertEquals("Peashooter", ShopMenuRegex.BUY.getGroup(command, "plantType"));
    }

    @Test
    @DisplayName("a plant name with a space survives, and keeps no trailing whitespace")
    void multiWordPlant() {
        // Half the roster: Snow Pea, Twin Sunflower, Gold Bloom, Bonk Choy. Under -t (?<plantType>\S+)
        // none of them matched at all, so the selective seed packet could not be bought for any of them.
        String command = "shop buy -i 3 -n 2 -t Snow Pea   ";
        assertTrue(ShopMenuRegex.BUY.matches(command), command);
        assertEquals("2", ShopMenuRegex.BUY.getGroup(command, "number"));
        assertEquals("Snow Pea", ShopMenuRegex.BUY.getGroup(command, "plantType"));
    }

    @Test
    @DisplayName("a malformed purchase is still refused")
    void malformedIsRefused() {
        // The lazy group must not have loosened the rest of the pattern.
        assertFalse(ShopMenuRegex.BUY.matches("shop buy -i x -n 1"));
        assertFalse(ShopMenuRegex.BUY.matches("shop buy -n 1"));
        assertFalse(ShopMenuRegex.BUY.matches("shop buy -i 3 -n 1 -t"));
    }
}
