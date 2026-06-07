package utils.storage;

import models.user.User;

import java.util.Map;

public class DatabaseManager {
    private Map<String , User> users;
    private DatabaseManager databaseManager = null;

    private DatabaseManager(){};
    public DatabaseManager getInstance(){return null;}
    public void saveAll(){};
    public void loadAll(){};
    public User findUser(String username){return null;}
    public void addUser(User newUser){};
    public boolean usernameExists(){return false;}
}
