package utils.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import models.user.User;
import utils.storage.records.UserRecord;

import java.io.*;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class DatabaseManager {
    private static final String DB_FILE_PATH = "users_database.json";

    private Map<String , User> users;
    private static DatabaseManager instance;

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private DatabaseManager(){
        this.users = new HashMap<>();
    }

    public static DatabaseManager getInstance(){
        if(instance == null){
            instance = new DatabaseManager();
            instance.loadAll();
        }
        return instance;
    }

    // The save file only ever holds plain-data UserRecords, never live domain objects. This is what
    // guarantees no entity / GameSession / Random can be dragged into (or choke) serialization.
    public void saveAll(){
        Map<String, UserRecord> records = new HashMap<>();
        for (Map.Entry<String, User> entry : users.entrySet()) {
            records.put(entry.getKey(), UserRecord.from(entry.getValue()));
        }
        try (Writer writer = new FileWriter(DB_FILE_PATH)) {
            gson.toJson(records, writer);
            System.out.println("Data saved successfully to " + DB_FILE_PATH);
        } catch (IOException e) {
            System.err.println("Error saving data to file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void loadAll(){
        File dbFile = new File(DB_FILE_PATH);
        if (!dbFile.exists()) {
            System.out.println("Database file not found. Starting with an empty database.");
            return;
        }
        try (Reader reader = new FileReader(dbFile)) {
            Type type = new TypeToken<Map<String, UserRecord>>() {}.getType();
            Map<String, UserRecord> records = gson.fromJson(reader, type);

            this.users = new HashMap<>();
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
                System.out.println("Data loaded successfully from " + DB_FILE_PATH);
            }
        } catch (IOException e) {
            System.err.println("Error loading data from file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // An account is self-consistent when the name it is filed under is the name it calls itself.
    private boolean isSelfConsistent(Map.Entry<String, UserRecord> entry){
        String recorded = entry.getValue().getUsername();
        return recorded != null && recorded.trim().equalsIgnoreCase(
                entry.getKey() == null ? "" : entry.getKey().trim());
    }

    private void loadRecord(Map.Entry<String, UserRecord> entry){
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
    private String resolveLoadName(String recordUsername, String jsonKey){
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
    private String keyOf(String username){
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

    public User findUser(String username){
        String key = keyOf(username);
        return key == null ? null : users.get(key);
    }

    // Re-key the roster after a rename. The map key IS the username, so mutating User alone left the
    // account filed under its old name: the new name found nobody and the old one still logged in.
    // Returns false when the account is unknown or the new name belongs to somebody else.
    public boolean renameUser(String oldUsername, String newUsername){
        String oldKey = keyOf(oldUsername);
        if (oldKey == null || newUsername == null) {
            return false;
        }
        String newKey = newUsername.trim();
        if (newKey.isEmpty()) {
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
    }

    // Remove an account outright. Returns false when there was nothing to remove.
    public boolean removeUser(String username){
        String key = keyOf(username);
        return key != null && users.remove(key) != null;
    }

    // Every registered player, for whole-game views such as the leaderboard. Returned read-only so
    // callers can iterate the roster without being able to mutate the live user map.
    public java.util.Collection<User> getAllUsers(){
        return java.util.Collections.unmodifiableCollection(users.values());
    }
    // Refuses to clobber an existing account instead of silently overwriting it (which used to wipe a
    // player's whole profile if a duplicate ever slipped past validation). Returns false if rejected.
    public boolean addUser(User newUser){
        if (newUser == null || newUser.getUsername() == null) {
            return false;
        }
        String name = newUser.getUsername().trim();
        if (name.isEmpty() || usernameExists(name)) {
            return false;
        }
        users.put(name, newUser);
        return true;
    }

    public boolean usernameExists(String username){
        return keyOf(username) != null;
    }

    public User getLoggedInUser() {
        for (User user : users.values()) {
            if (user.isStayLoggedIn()) {
                return user;
            }
        }
        return null;
    }
}
