package models.quests;

import models.map.Cell;

import java.util.Map;

public class QuestContext {


    private int totalSunCollected;
    private int plantsLost;
    private int totalZombiesKilled;
    private Map<String, Integer> killsByPlantType;
    private Cell[][] finalMapGrid;
    private long levelDurationTicks;


    public QuestContext(){};


    public void addSun(int amount){};
    public void incrementPlantsLost(){};
    public void recordKill(String killerPlantType){};
    public void setFinalMapState(Cell[][] grid){};
    public void updateLevelDuration(long currentTicks){};


}