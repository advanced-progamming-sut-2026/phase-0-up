package models.game;

import utils.Constants;

public class SeedPacket {
    private String plantType;
    private int cooldownDuration;
    private long lastPlantedTick;
    private boolean isBoosted;

    public SeedPacket(String plantType, int cooldownDuration) {
        this.plantType = plantType;
        this.cooldownDuration = cooldownDuration;
        this.lastPlantedTick = -1;
        this.isBoosted = false;
    }

    public boolean isBoosted() {
        return isBoosted;
    }

    public void setBoosted(boolean boosted) {
        isBoosted = boosted;
    }

    public boolean isReady(long currentTick){
        if (lastPlantedTick < 0){
            return true;
        }
        return currentTick - lastPlantedTick >= (long) cooldownDuration * Constants.TICKS_PER_SECOND;
    }
    public void updateLastPlantedTick(long currentTick){
        this.lastPlantedTick = currentTick;
    }
    public double getRemainingCooldownSeconds(long currentTick) {
        if (isReady(currentTick)) {
            return 0;
        }
        long totalCooldownTicks = (long) cooldownDuration * Constants.TICKS_PER_SECOND;
        long remainingTicks = totalCooldownTicks - (currentTick - lastPlantedTick);
        return remainingTicks / (double) Constants.TICKS_PER_SECOND;
    }
    public int getCooldownDuration() {
        return cooldownDuration;
    }
    public String getPlantType() {
        return plantType;
    }
}
