package utils.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import models.user.User;
import utils.storage.records.UserRecord;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

// Accounts in a JSON file on this machine.
//
// This is the original DatabaseManager, lifted out behind the AccountBackend interface and made safe
// for the server to use from several threads at once. The save-file repair logic below is carried over
// UNCHANGED and deliberately so: it encodes real damage seen in real save files (renames that orphaned
// an account, two records claiming one name) and every line of it was written in response to something
// that actually happened. It is not the place to be clever.
//
// Used by three of the four processes in the project:
//
//   the server         -- where it is the authoritative roster for every player
//   the terminal build -- which stays fully playable with no server at all, because `gradlew run` is
//                         the regression harness for every change made on the graphical side
//   tests              -- which get it by default, so nothing had to be rewired
public class LocalFileBackend implements AccountBackend {

    // What the client and terminal build have always used.
    public static final String DEFAULT_FILE = "users_database.json";
    // The server's own roster. A separate name so a server and a terminal build sharing a working
    // directory -- which is exactly what happens during development -- do not fight over one file.
    public static final String SERVER_FILE = "server_users.json";

    private final Path file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    // Concurrent because the server reads this from connection threads while a match thread is
    // mutating a Profile inside it. The original plain HashMap was correct for one terminal and is not
    // correct now.
    private final Map<String, User> users = new ConcurrentHashMap<>();

    // Guards the multi-step operations -- a rename is a remove plus a put, and the whole-file write is
    // a read of every entry. ConcurrentHashMap makes each individual operation atomic; it does not make
    // a SEQUENCE of them atomic, which is the thing that actually matters here.
    private final ReentrantLock lock = new ReentrantLock();

    public LocalFileBackend() {
        this(DEFAULT_FILE);
    }

    public LocalFileBackend(String filePath) {
        this.file = Path.of(filePath);
        load();
    }

    public Path file() {
        return file;
    }

    // ---- credentials ----------------------------------------------------------------------------
    //
    // Exactly what LoginCommand and ForgetPasswordCommand used to do inline, moved here unchanged --
    // same checks, same order, same sentences. The terminal build therefore behaves identically; what
    // changed is only that the comparison now happens behind the interface, so the remote backend can
    // do it on the server instead of shipping hashes to the client.

    @Override
    public AuthResult authenticate(String username, String passwordHash, boolean stayLoggedIn) {
        User user = findUser(username);
        if (user == null) {
            return AuthResult.refused("User not found");
        }
        if (!hashesMatch(passwordHash, user.getHashPassword())) {
            return AuthResult.refused("Wrong password");
        }
        lock.lock();
        try {
            if (stayLoggedIn) {
                // Only one account may be the auto-login one, so claiming it clears everybody else's.
                for (User other : users.values()) {
                    other.setStayLoggedIn(false);
                }
            }
            user.setStayLoggedIn(stayLoggedIn);
        } finally {
            lock.unlock();
        }
        return AuthResult.of(user, "Welcome back! The lawn missed you.");
    }

    @Override
    public RecoveryStart beginRecovery(String username, String email) {
        User user = findUser(username);
        if (user == null) {
            return RecoveryStart.refused("User not found!");
        }
        if (user.getEmail() == null || !user.getEmail().equalsIgnoreCase(email)) {
            return RecoveryStart.refused("That email doesn't match this gardener.");
        }
        return RecoveryStart.of(user.getSecurityQuestionIndex());
    }

    @Override
    public utils.Result verifyRecoveryAnswer(String username, String answerHash) {
        User user = findUser(username);
        if (user == null) {
            return new utils.Result(false, "User not found!");
        }
        if (!hashesMatch(answerHash, user.getSecurityAnswerHash())) {
            return new utils.Result(false, "Invalid answer!");
        }
        return new utils.Result(true, "");
    }

    @Override
    public utils.Result completeRecovery(String username, String answerHash, String newPasswordHash) {
        utils.Result verified = verifyRecoveryAnswer(username, answerHash);
        if (!verified.success()) {
            return verified;   // re-checked here, so the verify step cannot simply be skipped
        }
        User user = findUser(username);
        user.changePassword(newPasswordHash);
        flush();
        return new utils.Result(true, "Password changed successfully!");
    }

    @Override
    public utils.Result changePassword(String username, String currentPasswordHash,
                                       String newPasswordHash) {
        User user = findUser(username);
        if (user == null) {
            return new utils.Result(false, "User not found");
        }
        // The same two refusals ProfileCommands used to make inline, in the same order, with the same
        // sentences -- moved here so the check happens next to the stored hash rather than next to a
        // copy of it the caller happened to be holding.
        if (!hashesMatch(currentPasswordHash, user.getHashPassword())) {
            return new utils.Result(false, "Your old password isn't correct");
        }
        if (hashesMatch(newPasswordHash, user.getHashPassword())) {
            return new utils.Result(false, "New password is the one you already have!");
        }
        user.changePassword(newPasswordHash);
        flush();
        return new utils.Result(true, "");
    }

    // Constant-time, so the number of leading characters a guess got right cannot be read off how long
    // the answer took. String.equals returns early on the first mismatch, which over enough attempts
    // leaks a hash one character at a time.
    private static boolean hashesMatch(String offered, String stored) {
        if (offered == null || stored == null) {
            return false;
        }
        return java.security.MessageDigest.isEqual(offered.getBytes(StandardCharsets.UTF_8),
                stored.getBytes(StandardCharsets.UTF_8));
    }

    // ---- reading --------------------------------------------------------------------------------

    @Override
    public User findUser(String username) {
        String key = keyOf(username);
        return key == null ? null : users.get(key);
    }

    @Override
    public boolean usernameExists(String username) {
        return keyOf(username) != null;
    }

    @Override
    public Collection<User> getAllUsers() {
        return Collections.unmodifiableCollection(users.values());
    }

    @Override
    public User getLoggedInUser() {
        for (User user : users.values()) {
            if (user.isStayLoggedIn()) {
                return user;
            }
        }
        return null;
    }

    // ---- writing --------------------------------------------------------------------------------

    @Override
    public boolean addUser(User newUser) {
        if (newUser == null || newUser.getUsername() == null) {
            return false;
        }
        String name = newUser.getUsername().trim();
        if (name.isEmpty()) {
            return false;
        }
        lock.lock();
        try {
            // Checked and inserted under the lock, not with putIfAbsent: the check is
            // case-INSENSITIVE and the key is the typed casing, so two registrations racing on "Amir"
            // and "amir" would both pass a lock-free check and both insert.
            if (usernameExists(name)) {
                return false;
            }
            users.put(name, newUser);
            return true;
        } finally {
            lock.unlock();
        }
    }

    // Re-key the roster after a rename. The map key IS the username, so mutating User alone left the
    // account filed under its old name: the new name found nobody and the old one still logged in.
    @Override
    public boolean renameUser(String oldUsername, String newUsername) {
        if (newUsername == null) {
            return false;
        }
        String newKey = newUsername.trim();
        if (newKey.isEmpty()) {
            return false;
        }
        lock.lock();
        try {
            String oldKey = keyOf(oldUsername);
            if (oldKey == null) {
                return false;
            }
            String clash = keyOf(newKey);
            if (clash != null && !clash.equals(oldKey)) {
                return false;   // taken by a different account
            }
            User user = users.remove(oldKey);
            if (user == null) {
                return false;
            }
            user.changeUsername(newKey);
            users.put(newKey, user);
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean removeUser(String username) {
        lock.lock();
        try {
            String key = keyOf(username);
            return key != null && users.remove(key) != null;
        } finally {
            lock.unlock();
        }
    }

    // ---- the leaderboard ------------------------------------------------------------------------

    @Override
    public java.util.List<models.leaderboard.LeaderboardEntry> leaderboard(
            models.leaderboard.LbColumn column, boolean ascending) {
        // Rows built and sorted here, but the ORDERING is LbColumn's -- the username tie-break and
        // where a never-played score ranks are its rules, and every other caller asks it the same
        // question.
        //
        // Deliberately not routed through LeaderboardSystem, which would make this the only
        // utils -> controllers dependency in the codebase. Storage is below the controllers, and one
        // sorter is not worth inverting that for when the rule it would fetch already lives in models.
        java.util.List<models.leaderboard.LeaderboardEntry> rows = new java.util.ArrayList<>();
        for (User user : users.values()) {
            if (user != null && user.getUsername() != null) {
                rows.add(models.leaderboard.LeaderboardEntry.from(user));
            }
        }
        if (column != null) {
            rows.sort(column.comparator(ascending));
        }
        return rows;
    }

    // Offline there is nobody to submit to, and nothing to arbitrate: the profile has already recorded
    // the run through Profile.recordScoringGameRun, which applies the same "only if it beats the
    // record" rule the server does. So this reports what the profile holds rather than doing the work
    // twice -- and returns null when there is no signed-in player, which is the only honest answer.
    @Override
    public Integer submitScore(int meowPoints) {
        User user = getLoggedInUser();
        return user == null || user.getProfile() == null
                ? null : user.getProfile().getBestNumberOfMeowPoints();
    }

    // ---- persistence ----------------------------------------------------------------------------

    // The save file only ever holds plain-data UserRecords, never live domain objects. This is what
    // guarantees no entity / GameSession / Random can be dragged into (or choke) serialization.
    //
    // Written to a temporary file and then MOVED into place, which the original did not do. A crash or
    // a full disk partway through the old write left a truncated file -- and a truncated JSON object
    // does not load partially, it fails to parse, so one bad moment took out every account at once.
    // The move is the only step that is visible to a reader, and it either happened or it did not.
    @Override
    public void flush() {
        lock.lock();
        try {
            Map<String, UserRecord> records = new LinkedHashMap<>();
            for (Map.Entry<String, User> entry : users.entrySet()) {
                records.put(entry.getKey(), UserRecord.from(entry.getValue()));
            }
            writeAtomically(records);
            System.out.println("Data saved successfully to " + file);
        } catch (IOException e) {
            System.err.println("Error saving data to file: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    private void writeAtomically(Map<String, UserRecord> records) throws IOException {
        Path directory = file.toAbsolutePath().getParent();
        Path temp = Files.createTempFile(directory, file.getFileName().toString(), ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                gson.toJson(records, writer);
            }
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                // Some filesystems cannot promise atomicity across the rename. A plain replace is
                // still far better than writing into the live file in place, so it is the fallback
                // rather than a failure.
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private void load() {
        File dbFile = file.toFile();
        if (!dbFile.exists()) {
            System.out.println("Database file not found. Starting with an empty database.");
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, UserRecord>>() { }.getType();
            Map<String, UserRecord> records = gson.fromJson(reader, type);

            users.clear();
            if (records != null) {
                // Two passes, so the outcome never depends on HashMap iteration order. Self-consistent
                // records (JSON key already equals the username) are the trustworthy ones and claim
                // their name first; only then do mismatched records -- the ones a pre-fix rename
                // orphaned -- try to take the name they carry. Doing this in one pass let an orphan
                // grab a name out from under the account that legitimately owned it.
                for (Map.Entry<String, UserRecord> entry : records.entrySet()) {
                    if (entry.getValue() != null && isSelfConsistent(entry)) {
                        loadRecord(entry);
                    }
                }
                for (Map.Entry<String, UserRecord> entry : records.entrySet()) {
                    if (entry.getValue() != null && !isSelfConsistent(entry)) {
                        loadRecord(entry);
                    }
                }
                System.out.println("Data loaded successfully from " + file);
            }
        } catch (IOException e) {
            System.err.println("Error loading data from file: " + e.getMessage());
        } catch (RuntimeException e) {
            // A hand-edited or half-written file. Reported rather than thrown: the game starting with
            // an empty roster is recoverable, a crash on launch is not.
            System.err.println("Save file at " + file + " could not be parsed: " + e.getMessage());
        }
    }

    // An account is self-consistent when the name it is filed under is the name it calls itself.
    private boolean isSelfConsistent(Map.Entry<String, UserRecord> entry) {
        String recorded = entry.getValue().getUsername();
        return recorded != null && recorded.trim().equalsIgnoreCase(
                entry.getKey() == null ? "" : entry.getKey().trim());
    }

    private void loadRecord(Map.Entry<String, UserRecord> entry) {
        User user = entry.getValue().toUser();
        String name = resolveLoadName(entry.getValue().getUsername(), entry.getKey());
        user.changeUsername(name);
        users.put(name, user);
    }

    // Decides what name an account being loaded should be filed under, and never drops a player to do
    // it. A save written before renames re-keyed the roster is filed under the *old* username while
    // the record carries the new one, so the record's own username wins. But that repair can collide:
    // if a rename was let through onto a name another account legitimately owns, both records now
    // claim it. In that case the account falls back to the JSON key it is already filed under (and its
    // username field is corrected to match), which effectively undoes the invalid rename. Only if that
    // is taken too does it get a numeric suffix -- losing a profile is never an option.
    private String resolveLoadName(String recordUsername, String jsonKey) {
        String preferred = recordUsername != null && !recordUsername.isBlank()
                ? recordUsername.trim() : null;
        if (preferred != null && keyOf(preferred) == null) {
            return preferred;
        }
        String fallback = jsonKey != null && !jsonKey.isBlank() ? jsonKey.trim() : null;
        if (fallback != null && keyOf(fallback) == null) {
            if (preferred != null && !preferred.equals(fallback)) {
                System.err.println("Save file has two accounts claiming the name '" + preferred
                        + "'; keeping the second one as '" + fallback + "'.");
            }
            return fallback;
        }
        String base = preferred != null ? preferred : (fallback != null ? fallback : "player");
        int suffix = 2;
        while (keyOf(base + "-" + suffix) != null) {
            suffix++;
        }
        String unique = base + "-" + suffix;
        System.err.println("Save file has duplicate accounts named '" + base
                + "'; keeping this one as '" + unique + "'.");
        return unique;
    }

    // Usernames identify an account case-insensitively: "Amir" and "amir" are the same gardener, so
    // one cannot register over the other and either spelling logs you in. The map still keys on the
    // username as the player typed it, which keeps the save file readable and the display casing
    // intact -- only the lookup ignores case. The roster is a handful of users, so a scan is fine.
    private String keyOf(String username) {
        if (username == null) {
            return null;
        }
        String wanted = username.trim();
        if (wanted.isEmpty()) {
            return null;
        }
        for (String key : users.keySet()) {
            if (key != null && key.equalsIgnoreCase(wanted)) {
                return key;
            }
        }
        return null;
    }
}
