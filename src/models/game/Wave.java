package models.game;

import models.entities.zombies.Zombie;
import utils.Constants;

import java.util.ArrayList;
import java.util.List;

// One attack wave. Built from authored data (zombie aliases + budget + delay) by LevelFactory; the
// live Zombie list is filled at runtime by the WaveSystem when the wave actually spawns.
//
// The wave stays a passive model: it records what was spawned for it and answers questions about its
// own state (how much of it is dead, how hard it should be). Deciding when to spawn and building the
// zombies is the WaveSystem's job.
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

    // The WaveSystem writes the budget it actually spent back here, so a wave whose cost was derived
    // from the difficulty curve (rather than authored) still reports its real value afterwards.
    public void setWaveCost(int waveCost) {
        this.waveCost = waveCost;
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

    // Records a zombie the WaveSystem spawned for this wave and folds its starting HP (body + armor
    // layers) into the total, which is the denominator for the 75% next-wave threshold.
    public void addZombie(Zombie zombie) {
        if (zombie == null) {
            return;
        }
        zombies.add(zombie);
        if (zombie.getHealth() != null) {
            totalInitialHp += zombie.getHealth().getTotalHP();
        }
    }

    // Live HP still standing across every zombie this wave spawned.
    public int currentHp() {
        int total = 0;
        for (Zombie zombie : zombies) {
            if (zombie.getHealth() != null) {
                total += Math.max(0, zombie.getHealth().getTotalHP());
            }
        }
        return total;
    }

    // Fraction of this wave's starting HP that has been destroyed, 0.0 to 1.0. A wave that spawned
    // nothing reports 1.0 so an empty or unaffordable wave can never stall the sequence behind it.
    public double hpLostFraction() {
        if (totalInitialHp <= 0) {
            return 1.0;
        }
        double lost = (double) (totalInitialHp - currentHp()) / totalInitialHp;
        return Math.min(1.0, Math.max(0.0, lost));
    }

    public boolean isDefeated() {
        for (Zombie zombie : zombies) {
            if (zombie.getHealth() != null && !zombie.getHealth().isDead()) {
                return false;
            }
        }
        return true;
    }

    // How much harder this wave is than wave 1: each wave is 25% harder than the one before it, and
    // the last wave -- the "flag" wave -- is instead twice the difficulty of the wave before it.
    // A single-wave level has nothing to scale against, so it stays at the base budget.
    public double difficultyFactor() {
        if (number <= 1) {
            return 1.0;
        }
        if (isFinal) {
            return Math.pow(Constants.WAVE_DIFFICULTY_INCREMENT, number - 2) * Constants.FLAG_WAVE_MULTIPLIER;
        }
        return Math.pow(Constants.WAVE_DIFFICULTY_INCREMENT, number - 1);
    }
}
