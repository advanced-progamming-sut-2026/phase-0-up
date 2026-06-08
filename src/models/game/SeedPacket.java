package models.game;

public class SeedPacket {
    private String plantType;
    private int cooldownDuration;
    private long lastPlantedTick;

    public boolean isReady(){return true;}
    public void updateLastPlantedTick(){}

}
