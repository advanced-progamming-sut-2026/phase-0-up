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

        // `count` shots, ALWAYS -- cycling back through the targets when there are fewer of them than
        // shots, and raining on the plant's own lane when there are none at all.
        //
        // It used to stop at `i < targets.size()`, which quietly turned "a barrage of ten" into "one
        // lob per zombie currently on screen". Every pult's plant food therefore looked identical and
        // tiny -- three zombies on the lawn meant three shots, whether the plant was a Cabbage-pult
        // (10) or a Melon-pult (5) -- and feeding one on a clear lawn did nothing whatsoever.
        Collections.shuffle(targets);
        for (int i = 0; i < count; i++) {
            int lane = targets.isEmpty()
                    ? sourcePlant.getY()
                    : targets.get(i % targets.size()).getMovement().getPositionY();
            lobber.lobInLane(sourcePlant, gameSession, lane);
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
