package models.greenhouse;

import models.entities.plants.Plant;

import java.util.List;

public class GreenHouse {
    private Pot[] pots;
    private List<Pot> unlockedPots;
    private final int rows = 4;
    private final int cols = 5;

    public void increaseSpeed(int x, int y){}
    public String showStatus(){return null;}
    public Plant plantPlot(int x, int y){return null;}
    public Plant harvest(int x, int y){return null;}
    public void harvestReward(Plant plant){}
    public List<Pot> getUnlockedPots() {return unlockedPots;}
}
