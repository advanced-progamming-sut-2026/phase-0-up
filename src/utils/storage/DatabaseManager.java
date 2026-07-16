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
                for (Map.Entry<String, UserRecord> entry : records.entrySet()) {
                    if (entry.getValue() != null) {
                        this.users.put(entry.getKey(), entry.getValue().toUser());
                    }
                }
                System.out.println("Data loaded successfully from " + DB_FILE_PATH);
            }
        } catch (IOException e) {
            System.err.println("Error loading data from file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public User findUser(String username){
        return users.get(username);
    }
    public void addUser(User newUser){
        if (newUser != null && newUser.getUsername() != null) {
            users.put(newUser.getUsername(), newUser);
        }
    };
    public boolean usernameExists(String username){
        return users.containsKey(username);
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
