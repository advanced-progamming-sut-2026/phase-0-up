package models.map.Terrains;

import models.entities.plants.Plant;
import models.entities.plants.PlantTags;
import models.entities.zombies.Zombie;
import models.map.GameMap;

public class WaterTerrain extends Terrain{
    private int waterLevel;

    public WaterTerrain() {
        this.plantable = true;
        this.symbol = '!';
    }

    public void increaseWaterLevel(int m , GameMap gameMap){
        waterLevel += m;
        checkForWaterPlants(gameMap);
    }

    public void decreaseWaterLevel(int m , GameMap gameMap){
        returnToDefualtCell(gameMap , m);
        waterLevel -= m;
    }

    private void returnToDefualtCell(GameMap gameMap , int m) {
        for(int j = 0 ; j < 5; j++){
            for(int i = (8 - waterLevel) ; i > (8 - m) ;i++){
                gameMap.getRow(j).cellAt(i).getTerrain().remove(this);
                gameMap.getRow(j).cellAt(i).setFlooded(false);
            }
        }
    }

    private void checkForWaterPlants(GameMap gameMap) {
        for(int j = 0 ; j < 5; j++){
            for(int i = 8 ; i > (8 - waterLevel) ;i--){
                Plant p = gameMap.getRow(j).cellAt(i).getPlantStack().peek();
                if(!p.getTags().contains(PlantTags.WATER)){
                    p.getHealth().takeDamage(p.getHealth().getMaxHp());
                }
                gameMap.getRow(j).cellAt(i).addTerrain(this);
                gameMap.getRow(j).cellAt(i).setFlooded(true);
            }
        }
    }

    @Override
    public void effect(Zombie z, Plant p) {

    }
}
