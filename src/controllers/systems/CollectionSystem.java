package controllers.systems;

import models.user.Profile;

public class CollectionSystem {
    private static CollectionSystem instance;

    private CollectionSystem() {}

    public static synchronized CollectionSystem getInstance() {
        if (instance == null) {
            instance = new CollectionSystem();
        }
        return instance;
    }

    public void purchasePlant(Profile profile, String plantName){}
    public void upgradePlant(Profile profile, String plantName){}
}
