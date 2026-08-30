package models.entities.plants.FoodStrategies;

import models.entities.plants.Plant;
import models.entities.plants.abilities.PlantAbility;
import models.entities.plants.abilities.ShootProjectileAbility;
import models.game.GameSession;

// PLASMA_ORB plant-food: Citron fires one plasma orb that nothing stops, clearing its lane.
//
// This replaced LaneClearStrategy for Citron. That one applied the damage instantly and invisibly to
// every zombie in the row, so a plant whose whole character is a charged shot killed a lane with
// nothing crossing it -- the plant flashed and the zombies fell over. The orb does the same damage by
// flying through them, which is both what the game does and what the player can see happening.
public class PlasmaOrbStrategy implements PlantFoodStrategy {
    private int damage;

    public PlasmaOrbStrategy(int damage) {
        this.damage = damage;
    }

    @Override
    public void executeEffect(Plant sourcePlant, GameSession gameSession) {
        for (PlantAbility ability : sourcePlant.getAbilities()) {
            if (ability instanceof ShootProjectileAbility shooter) {
                shooter.queuePlasmaOrb(damage);
                return;
            }
        }
    }
}
