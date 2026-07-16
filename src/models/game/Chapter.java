package models.game;

public class Chapter {
    private String name;
    private Level[] levels;
    private boolean unlocked;
    private int zombiesKilledInThisChapter;
    private EnvironmentType environment;

    public Chapter(String name, EnvironmentType environment, Level[] levels) {
        this.name = name;
        this.environment = environment;
        this.levels = levels;
        this.unlocked = false;
        this.zombiesKilledInThisChapter = 0;
    }

    // A chapter is complete once every one of its levels has been cleared.
    public boolean isComplete() {
        if (levels == null) {
            return false;
        }
        for (Level level : levels) {
            if (!level.isCompleted()) {
                return false;
            }
        }
        return true;
    }

    public String getName() {
        return name;
    }

    public Level[] getLevels() {
        return levels;
    }

    public EnvironmentType getEnvironment() {
        return environment;
    }

    public boolean isUnlocked() {
        return unlocked;
    }

    public void setUnlocked(boolean unlocked) {
        this.unlocked = unlocked;
    }

    public int getZombiesKilledInThisChapter() {
        return zombiesKilledInThisChapter;
    }

    public void addZombiesKilled(int amount) {
        this.zombiesKilledInThisChapter += amount;
    }
}
