package utils.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import models.user.User;

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

    public void saveAll(){
        try (Writer writer = new FileWriter(DB_FILE_PATH)) {
            gson.toJson(users, writer);
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
            Type type = new TypeToken<Map<String, User>>() {}.getType();
            Map<String, User> loadedUsers = gson.fromJson(reader, type);

            if (loadedUsers != null) {
                this.users = loadedUsers;
                System.out.println("Data loaded successfully from " + DB_FILE_PATH);
            } else {
                this.users = new HashMap<>();
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
}
