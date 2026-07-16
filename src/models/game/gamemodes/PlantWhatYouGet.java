package models.game.gamemodes;

// Special level: the player gets one fixed pile of sun and no sky drops for the rest of the level.
public class PlantWhatYouGet extends StandardMode {
    private int initialSun;

    @Override
    public boolean allowsSkySun() {
        return false;
    }
}
