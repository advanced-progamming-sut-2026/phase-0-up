package models.game;

public class SeedPacket {
    private String plantType;
    private int cooldownDuration;
    private long lastPlantedTick;
    private boolean isBoosted = false;

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

    public boolean isReady(){return true;}
    public void updateLastPlantedTick(){}

    public String getPlantType() {
        return plantType;
    }
}
