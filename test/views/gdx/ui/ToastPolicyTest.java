package views.gdx.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Which of the model's sentences reach the screen.
//
// Pinned as a test because the policy is a set of regexes over sentences that live somewhere else
// entirely -- the model's narration -- and the failure mode is silent in both directions: a pattern
// that stops matching puts the spam back, and one that matches too much swallows the refusal the
// player needed to read. Neither shows up in a screenshot of a correct-looking frame.
//
// Every string here is copied from the sentence the model actually emits, so this doubles as the
// written record of the audit: 37 narration sentences and ~45 command results were swept once and
// classified, and these are the representatives of each group.
class ToastPolicyTest {

    private static ToastPolicy.Kind of(String message) {
        return ToastPolicy.classify(message, true);
    }

    private static void suppressed(String... messages) {
        for (String message : messages) {
            assertEquals(ToastPolicy.Kind.NONE, of(message), message);
        }
    }

    @Test
    void collectingSunAndCoinsIsNeverAnnounced() {
        // Sun is collected by sweeping the cursor over it, so this fired several times a second.
        suppressed("Collected 25 sun.",
                "Collected 50 sun.",
                "A sun-maker drops 25 sun at (4, 2).",
                // Coins, gems and pots already fly to their counter and caption themselves.
                "A zombie dropeed a coin; you have 50 coins now.",
                "A zombie dropeed a gem; you have 1 gems now.",
                "The glowing zombie dropeed a plant food; you have 1 plant foods now.",
                "The ZombieRa drops 75 of the sun it stole onto the lawn -- go and get it!");
    }

    @Test
    void plantingAndDiggingSpeakForThemselves() {
        suppressed("\"Peashooter\" is in the ground at (3, 2). Hold the line!",
                "Plant placed successfully.",
                "Plant removed successfully.",
                "Platform placed.",
                "Protective cover removed.",
                "Pea Pod stacked successfully! Current heads: 2",
                "You tore up the Sunflower at (0, 0).");
    }

    @Test
    void zombieAbilitiesWithArtOfTheirOwnAreSilent() {
        suppressed("Zombie ZombieDefault spawned at wave 1 in lane 4 which costed 100.",
                "A ZombieDefault claws its way up at (6, 0).",
                "Zombie of type ZombieDefault is dead at (3, 1)",
                "Plant Sunflower at (0, 0) is destroyed.",
                "The ZombieProspector wanders off the far end of the lawn and is gone.",
                "ZombieGargantuar hurls its Imp over your defences onto (2, 3)!",
                "ZombieIceAgeHunter takes aim at Peashooter at (3, 1).",
                "ZombieIceAgeHunter hurls ice at Peashooter at (3, 1).",
                "ZombieTurquoiseSkull fires a laser beam through the next 3 tiles.",
                "ZombieCrystalSkull levels its skull at (6, 2) and takes aim down the next 4 tiles.",
                "The ZombieCrystalSkull powers up at (6, 2) and starts siphoning your sun.",
                "The ZombieCrystalSkull powers down at (6, 2); its beam is charged.",
                "The ZombieRa powers up at (4, 1) and reaches for the sun.",
                "The ZombieRa powers down at (4, 1); nothing left to reel in.",
                "The King Zombie raises his sceptre at (8, 2).",
                "ZombieWizard raises its staff at (6, 1).",
                "Peashooter is hexed into a sheep at (3, 1).",
                "Peashooter shakes off the hex at (3, 1) and returns to normal.",
                "ZombieBotanyPeashooter spits a pea from (7, 2) at Sunflower at (3, 2).",
                "ZombieBeachOctopus winds up an octopus at (7, 2).",
                "ZombieBeachOctopus flings an octopus from (7, 2) onto Wall-nut at (5, 2).",
                "ZombieModernAllStar tackles Peashooter at (4, 0).",
                "ZombieArcade rams its arcade machine into Wall-nut at (6, 1).",
                "ZombieArcade's arcade machine falls apart at (5, 1); it walks and eats normally now.",
                "The King Zombie knights a peasant zombie at (7, 2).",
                "The Tomb Raiser starts chanting for the dead.",
                "The Tomb Raiser raises a grave at (5, 3).",
                "A grave heaves up out of the ground at (4, 2).",
                "The Troglobite's ice block crushes Peashooter at (2, 1).",
                "A frozen block shatters at (4, 2) and the Yeti Imp inside it hits the ground running!",
                "The Barrel Roller falls at (4, 4).",
                "The barrel bursts open at (4, 4).",
                "The rolling barrel crushes Sunflower at (3, 4).",
                "The pianist strikes up a tune and shoves the lane.",
                "The piano smashes into a spiky Cactus at (2, 2).",
                "The Fisherman Zombie hooks Peashooter at (1, 3).",
                "The Fisherman Zombie wades in at (8, 3) and settles down to fish.",
                "The Fisherman Zombie casts its line at (8, 3).",
                "The Fisherman Zombie reels Peashooter one tile to the right, to (4, 3).",
                "The Snorkel Zombie dives underwater at (6, 2).",
                "The Jester Zombie whirls into a spin at (5, 1).",
                "ZombieProspector's dynamite fizzles out in the ice at (3, 0).",
                "You tore up the ZombieNewspaper's newspaper at (5, 2) "
                        + "-- now he is furious, and coming in fast.",
                "Boom! ZombieProspector's dynamite explodes at (5, 2) and blasts it back to (0, 2).",
                "ZombieExplorer's torch is snuffed out by the ice at (2, 4).",
                "ZombieIceAgeHunter ignores the chill at (3, 3).");
    }

    @Test
    void shotsBlastsAndCheatsAreSilent() {
        suppressed("Jalapeno detonates at (4, 4)!",
                "Radioactive sun detonates at (3, 1).",
                "Grapeshot bursts into a spray of bouncing grapes at (5, 2).",
                "A grave crumbles at (6, 3).",
                "plant Sunflower produced a sun at (0, 2)",
                "New normal sun is dropping at position (4, 4)",
                "Cooldowns gone! Plant as fast as your fingers allow.",
                "One fresh plant food, coming up! You now have 2.",
                "KA-BOOM! Every zombie on the lawn is vapourised.",
                "Sunny day! +500 sun. You now have 1200.",
                "Got it! A Peashooter joins your hand.",
                "Thunk! The wall-nut rolls off down the lane.",
                "The tornado drops ZombieDefault into lane 2, 3 column(s) past the edge.");
    }

    // An NPC saying it out loud and a toast saying it at the same time is one event announced twice.
    @Test
    void anythingAnNpcSaysIsNotAlsoAToast() {
        suppressed("Night Ops! Not a single sun will fall from this sky.",
                "Vasebreaker! No seed selection and no sun here.",
                "Wall-nut Bowling! You pick no plants and no sun falls.",
                "Dead Line! A trip-wire runs down column 5.",
                "I, Zombie! You're on the undead side this time.",
                "Beghouled! The lawn is a match-3 board.",
                "Zombotany! Plant-headed zombies are coming.",
                "Scoring Game for 2026-08-25.",
                "The final wave has come.");
    }

    @Test
    void theWarningsTheSpecNamesAreAlerts() {
        assertEquals(ToastPolicy.Kind.ALERT, of("Wave 1 started."));
        assertEquals(ToastPolicy.Kind.ALERT, of("Wave 3 started."));
        assertEquals(ToastPolicy.Kind.ALERT, of("A zombie claws up from a necromancy grave at (5, 2)."));
        assertEquals(ToastPolicy.Kind.ALERT, of("A zombie surfaces from the low tide at (7, 3)."));
        assertEquals(ToastPolicy.Kind.ALERT, of("A freezing wind sweeps through row 2."));
        assertEquals(ToastPolicy.Kind.ALERT, of("The tide rises and floods column 7."));
        assertEquals(ToastPolicy.Kind.ALERT, of("A tornado tears across the desert and hurls the wave in."));
        assertEquals(ToastPolicy.Kind.ALERT, of("The lawn mower in the row 2 has been used."));
        assertEquals(ToastPolicy.Kind.ALERT,
                of("The King Zombie takes his throne at (8, 2) and starts handing out knighthoods."));
        assertEquals(ToastPolicy.Kind.ALERT,
                of("The Jalapeno Zombie in lane 3 is about to blow -- take it down!"));
    }

    // A refusal is invisible on the board by definition, so it is the one class that is never filtered.
    @Test
    void everyRefusalIsShown() {
        assertEquals(ToastPolicy.Kind.ERROR,
                ToastPolicy.classify("Not enough sun for a \"Peashooter\".", false));
        assertEquals(ToastPolicy.Kind.ERROR,
                ToastPolicy.classify("This cell does not contain a plant.", false));
        // Even a sentence the suppression list would otherwise match: success is what decides first.
        assertEquals(ToastPolicy.Kind.ERROR, ToastPolicy.classify("No sun to collect at (2, 2).", false));
        assertEquals(ToastPolicy.Kind.ERROR, ToastPolicy.classify("Plant placed successfully.", false));
    }

    @Test
    void gainsWithNoAnimationOfTheirOwnStillShow() {
        assertEquals(ToastPolicy.Kind.REWARD, of("Quest complete: Sunny Days"));
        assertEquals(ToastPolicy.Kind.REWARD, of("Pot at (2, 1) unlocked successfully!"));
        // An unclassified sentence is shown rather than swallowed: a feature that looks broken with
        // nothing in the log to say why is the worse failure of the two.
        assertEquals(ToastPolicy.Kind.REWARD, of("Something nobody has classified yet."));
    }
}
