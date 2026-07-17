package models.entities.plants.FoodStrategies;

import models.entities.plants.Plant;
import models.entities.plants.abilities.PlantAbility;
import models.entities.plants.abilities.ShootProjectileAbility;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import utils.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// LOB_BARRAGE plant-food: lobs the plant's projectile at N random zombies (Cabbage-pult, Melon-pult, Pepper-pult, ...).
public class LobBarrageStrategy implements PlantFoodStrategy {
    private int count;

    public LobBarrageStrategy(int count) {
        this.count = count;
    }

    @Override
    public void executeEffect(Plant sourcePlant, GameSession gameSession) {
        ShootProjectileAbility lobber = findLobber(sourcePlant);
        if (lobber == null) return;

        List<Zombie> targets = new ArrayList<>();
        for (int row = 0; row < Constants.BOARD_ROWS; row++) {
            List<Zombie> zombies = gameSession.getMap().getRow(row).getZombies();
            if (zombies == null) continue;
            for (Zombie z : zombies) {
                if (z.isTargetable()) {
                    targets.add(z);
                }
            }
        }

        Collections.shuffle(targets);
        for (int i = 0; i < count && i < targets.size(); i++) {
            lobber.lobInLane(sourcePlant, gameSession, targets.get(i).getMovement().getPositionY());
        }
    }

    private ShootProjectileAbility findLobber(Plant plant) {
        for (PlantAbility ability : plant.getAbilities()) {
            if (ability instanceof ShootProjectileAbility) {
                return (ShootProjectileAbility) ability;
            }
        }
        return null;
    }
}
