package utils.gameinitializers;

public class GameInitializer {
    public void loadAllData() {
        PlantInitializer.loadAllPlants();
        ZombieInitializer.loadAllZombies();
    }
}
