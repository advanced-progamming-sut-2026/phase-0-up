package models.game;

import models.entities.zombies.Zombie;

import java.util.ArrayList;
import java.util.List;

// One attack wave. Built from authored data (zombie aliases + budget + delay) by LevelFactory; the
// live Zombie list is filled at runtime by the WaveSystem when the wave actually spawns.
public class Wave {
    private int number;
    private boolean isFinal;
    private int waveCost;
    private int delay;
    private List<String> zombieAliases;
    private List<Zombie> zombies;
    private int totalInitialHp;

    public Wave() {
        this.zombieAliases = new ArrayList<>();
        this.zombies = new ArrayList<>();
    }

    public Wave(int number, boolean isFinal, int waveCost, int delay, List<String> zombieAliases) {
        this.number = number;
        this.isFinal = isFinal;
        this.waveCost = waveCost;
        this.delay = delay;
        this.zombieAliases = zombieAliases != null ? zombieAliases : new ArrayList<>();
        this.zombies = new ArrayList<>();
    }

    public int getNumber() {
        return number;
    }

    public boolean isFinal() {
        return isFinal;
    }

    public int getWaveCost() {
        return waveCost;
    }

    public int getDelay() {
        return delay;
    }

    public List<String> getZombieAliases() {
        return zombieAliases;
    }

    public List<Zombie> getZombies() {
        return zombies;
    }

    public int getTotalInitialHp() {
        return totalInitialHp;
    }

    public void setTotalInitialHp(int totalInitialHp) {
        this.totalInitialHp = totalInitialHp;
    }

    public void spawn() { }

    public void isDefeated() { }

    public double difficultyFactor() {
        return 0;
    }
}
