package controllers.systems;

import models.user.Profile;

public class GreenhouseSystem {
    private static GreenhouseSystem instance;

    private GreenhouseSystem() {}

    public static synchronized GreenhouseSystem getInstance() {
        if (instance == null) {
            instance = new GreenhouseSystem();
        }
        return instance;
    }


    public void plantPot(int x, int y, Profile profile){}
    public void collectPot(int x, int y, Profile profile){}
    public void updateGrow(int x, int y, Profile profile){}
    public void unlockPot(int x, int y, Profile profile){}
    public void speedUpGrowth(Profile profile, int x, int y){}
    private String getRandomPlant(Profile p){return null;}

}
