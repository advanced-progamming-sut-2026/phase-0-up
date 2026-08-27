package utils.storage;

import models.user.Gender;
import models.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The account roster on disk.
//
// Every test here runs against a @TempDir, never the project's real users_database.json -- a storage
// test that writes to the actual save file is one bad assertion away from deleting somebody's
// progress.
class LocalFileBackendTest {

    @TempDir
    Path directory;

    // ---- round trip -----------------------------------------------------------------------------

    @Test
    @DisplayName("an account survives a save and a reload")
    void accountsRoundTrip() {
        Path file = directory.resolve("users.json");

        LocalFileBackend writing = new LocalFileBackend(file.toString());
        User amir = user("Amir");
        amir.getProfile().setCoins(4242);
        amir.getProfile().recordScoringGameRun(777);
        assertTrue(writing.addUser(amir));
        writing.flush();

        LocalFileBackend reading = new LocalFileBackend(file.toString());
        User loaded = reading.findUser("Amir");
        assertNotNull(loaded);
        assertEquals(4242, loaded.getProfile().getCoins());
        assertEquals(777, loaded.getProfile().getBestNumberOfMeowPoints());
    }

    @Test
    @DisplayName("a missing file is an empty roster, not a crash")
    void missingFileStartsEmpty() {
        LocalFileBackend backend = new LocalFileBackend(directory.resolve("nothing.json").toString());
        assertTrue(backend.getAllUsers().isEmpty());
        assertNull(backend.findUser("anyone"));
    }

    @Test
    @DisplayName("an unparseable save file is reported, not thrown -- launching must still work")
    void corruptFileDoesNotPreventStartup() throws IOException {
        Path file = directory.resolve("broken.json");
        Files.writeString(file, "{ this is not json", StandardCharsets.UTF_8);

        // A crash on launch is unrecoverable for the player; an empty roster is not. They can still
        // reach the register screen, which is the difference between a bad day and a lost account.
        LocalFileBackend backend = new LocalFileBackend(file.toString());
        assertTrue(backend.getAllUsers().isEmpty());
    }

    // ---- the roster rules -----------------------------------------------------------------------

    @Test
    @DisplayName("usernames identify an account case-insensitively")
    void lookupIgnoresCase() {
        LocalFileBackend backend = new LocalFileBackend(directory.resolve("u.json").toString());
        backend.addUser(user("Amir"));

        assertNotNull(backend.findUser("amir"));
        assertNotNull(backend.findUser("AMIR"));
        assertNotNull(backend.findUser("  Amir  "));
        assertTrue(backend.usernameExists("aMiR"));

        // Which is also what makes registration globally unique in the sense the spec means: two
        // people cannot take the same name by spelling it differently.
        assertFalse(backend.addUser(user("amir")), "a differently-cased duplicate must be refused");
    }

    @Test
    @DisplayName("a duplicate registration is refused rather than overwriting a profile")
    void duplicateIsRefused() {
        LocalFileBackend backend = new LocalFileBackend(directory.resolve("u.json").toString());
        User first = user("Amir");
        first.getProfile().setCoins(9999);
        backend.addUser(first);

        assertFalse(backend.addUser(user("Amir")));
        // The point of refusing: overwriting used to wipe the existing player's whole profile.
        assertEquals(9999, backend.findUser("Amir").getProfile().getCoins());
    }

    @Test
    @DisplayName("a rename re-keys the roster, so the new name is the one that logs in")
    void renameRekeys() {
        LocalFileBackend backend = new LocalFileBackend(directory.resolve("u.json").toString());
        backend.addUser(user("Amir"));

        assertTrue(backend.renameUser("Amir", "Parsa"));
        assertNotNull(backend.findUser("Parsa"));
        // The map key IS the username; mutating User alone left the account filed under its old name,
        // so the new name found nobody and the old one still logged in.
        assertNull(backend.findUser("Amir"));
        assertEquals("Parsa", backend.findUser("Parsa").getUsername());
    }

    @Test
    @DisplayName("a rename onto somebody else's name is refused")
    void renameOntoATakenNameIsRefused() {
        LocalFileBackend backend = new LocalFileBackend(directory.resolve("u.json").toString());
        backend.addUser(user("Amir"));
        backend.addUser(user("Parsa"));

        assertFalse(backend.renameUser("Amir", "Parsa"));
        assertNotNull(backend.findUser("Amir"));
        assertNotNull(backend.findUser("Parsa"));
    }

    @Test
    @DisplayName("the stay-signed-in account is the one that gets auto-logged-in")
    void stayLoggedInIsFound() {
        LocalFileBackend backend = new LocalFileBackend(directory.resolve("u.json").toString());
        backend.addUser(user("Amir"));
        User parsa = user("Parsa");
        parsa.setStayLoggedIn(true);
        backend.addUser(parsa);

        assertEquals("Parsa", backend.getLoggedInUser().getUsername());
    }

    // ---- durability -----------------------------------------------------------------------------

    @Test
    @DisplayName("saving leaves no temporary files behind")
    void saveCleansUpAfterItself() throws IOException {
        Path file = directory.resolve("users.json");
        LocalFileBackend backend = new LocalFileBackend(file.toString());
        backend.addUser(user("Amir"));
        backend.flush();
        backend.flush();

        try (Stream<Path> entries = Files.list(directory)) {
            List<String> names = entries.map(p -> p.getFileName().toString()).toList();
            assertEquals(List.of("users.json"), names,
                    "the temp file the atomic write goes through must not survive it");
        }
    }

    @Test
    @DisplayName("an interrupted-looking write never leaves a half-written save in place")
    void saveIsAllOrNothing() throws IOException {
        Path file = directory.resolve("users.json");
        LocalFileBackend backend = new LocalFileBackend(file.toString());
        backend.addUser(user("Amir"));
        backend.flush();

        String afterFirst = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(afterFirst.contains("Amir"));

        // The real guarantee is that the file is written elsewhere and MOVED into place, so a reader
        // only ever sees a complete document. What is checkable here is the consequence: every flush
        // leaves a file that parses. The old in-place write could leave a truncated one, and a
        // truncated JSON object does not load partially -- it fails outright, taking every account
        // with it.
        for (int i = 0; i < 20; i++) {
            backend.addUser(user("player" + i));
            backend.flush();
            assertTrue(new LocalFileBackend(file.toString()).usernameExists("Amir"),
                    "the roster must be readable after every single flush");
        }
        assertEquals(21, new LocalFileBackend(file.toString()).getAllUsers().size());
    }

    @Test
    @DisplayName("concurrent registrations and saves do not lose or duplicate an account")
    void concurrentWritesAreSafe() throws Exception {
        // The server does exactly this: connection threads registering and syncing profiles while
        // other threads are saving. The original plain HashMap behind a singleton was correct for one
        // terminal and would corrupt here.
        Path file = directory.resolve("users.json");
        LocalFileBackend backend = new LocalFileBackend(file.toString());

        int threads = 8;
        int perThread = 25;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            final int id = t;
            Thread worker = new Thread(() -> {
                try {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        backend.addUser(user("p" + id + "-" + i));
                        if (i % 5 == 0) {
                            backend.flush();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
            worker.setDaemon(true);
            worker.start();
        }
        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS), "workers did not finish");

        assertEquals(threads * perThread, backend.getAllUsers().size());
        backend.flush();
        assertEquals(threads * perThread, new LocalFileBackend(file.toString()).getAllUsers().size());
    }

    @Test
    @DisplayName("two threads racing on the same new username -- exactly one wins")
    void concurrentDuplicateRegistrationHasOneWinner() throws Exception {
        // The check is case-insensitive but the key is the typed casing, so a lock-free
        // check-then-put would let "Amir" and "amir" both pass and both insert. That is the global
        // uniqueness rule the spec asks for, failing under exactly the load a server puts on it.
        LocalFileBackend backend = new LocalFileBackend(directory.resolve("u.json").toString());

        int threads = 16;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        java.util.concurrent.atomic.AtomicInteger accepted =
                new java.util.concurrent.atomic.AtomicInteger();

        for (int t = 0; t < threads; t++) {
            String spelling = (t % 2 == 0) ? "Amir" : "amir";
            Thread worker = new Thread(() -> {
                try {
                    start.await();
                    if (backend.addUser(user(spelling))) {
                        accepted.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
            worker.setDaemon(true);
            worker.start();
        }
        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS));

        assertEquals(1, accepted.get(), "exactly one registration may win the name");
        assertEquals(1, backend.getAllUsers().size());
    }

    private static User user(String username) {
        return new User(username, username, username + "@example.com", Gender.MALE,
                PasswordHasher.hash("pw"), 0, SecurityAnswer.hash("answer"));
    }
}
