package models.game;

public class Chapter {
    private String name;
    private Level[] levels;
    private boolean unlocked;
    private int zombiesKilledInThisChapter;
    private EnvironmentType environment;

    public boolean isComplete(){return false;}
    public String getName(){return name;}
    public Level[] getLevels(){return levels;}
}
