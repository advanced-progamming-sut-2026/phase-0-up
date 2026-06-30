package models.entities.plants.abilities;

import models.entities.plants.Plant;
import models.entities.projectiles.Projectile;
import models.entities.projectiles.ProjectileType;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import utils.Constants;

import java.util.List;

public class DiagonalShootAbility extends PlantAbility {
    private ProjectileType projectileType;
    private int damage;
    private double speed;

    public DiagonalShootAbility(int actionInterval, ProjectileType projectileType, int damage, double speed) {
        super(actionInterval);
        this.projectileType = projectileType;
        this.damage = damage;
        this.speed = speed;
    }

    @Override
    public boolean canExecute(Plant owner, GameSession gameSession) {
        int currentY = owner.getY();
        //in the real game diagonal zombies gets checked but for simplification we only check adjacent rows
        for (int rowOffset = -1; rowOffset <= 1; rowOffset++) {
            int targetRow = currentY + rowOffset;

            if (targetRow >= 0 && targetRow < Constants.BOARD_ROWS) {
                List<Zombie> zombies = gameSession.getMap().getRow(targetRow).getZombies();
                if (zombies != null && !zombies.isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void execute(Plant owner, GameSession gameSession) {
        double diagonalSpeed = speed / 1.414;
        //arrays of directions
        double[][] directions = {
                {diagonalSpeed, -diagonalSpeed},
                {diagonalSpeed, diagonalSpeed},
                {-diagonalSpeed, -diagonalSpeed},
                {-diagonalSpeed, diagonalSpeed}
        };

        for (double[] dir : directions) {
            Projectile projectile = new Projectile(
                    owner.getX() + 0.5,
                    owner.getY(),
                    projectileType,
                    damage,
                    dir[0], // speedX
                    dir[1], // speedY
                    owner
            );

            gameSession.getMap().getRow(owner.getY()).addProjectile(projectile);
        }
    }
}
