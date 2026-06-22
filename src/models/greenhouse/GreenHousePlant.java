package models.greenhouse;

import utils.Constants;

public class GreenHousePlant {
    private final String name;
    private final boolean isMarigold;
    private final Long growthDuration;

    public static final long MARIGOLD_TIME = 2L * 60 * 60 * 1000;
    public static final long RANDOM_PLANT_TIME = 8L * 60 * 60 * 1000;

    public GreenHousePlant(String name, boolean isMarigold) {
        this.name = name;
        this.isMarigold = isMarigold;
        this.growthDuration = isMarigold ? MARIGOLD_TIME : RANDOM_PLANT_TIME;
    }

    public String getName() {
        return name;
    }

    public boolean isMarigold() {
        return isMarigold;
    }

    public Long getGrowthDuration() {
        return growthDuration;
    }
}
