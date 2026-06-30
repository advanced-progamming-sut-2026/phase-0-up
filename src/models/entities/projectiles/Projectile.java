package models.entities.projectiles;

import models.entities.Entity;
import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import models.map.GameMap;
import utils.Constants;

import java.util.List;

public class Projectile extends Entity {
    private Plant shooter;
    private ProjectileType type;
    private int damage;

    private double speedX;
    private double speedY;
    private double exactY;

    private boolean isDestroyed;

    public Projectile(double x, double startY, ProjectileType type, int damage, double speedX, double speedY, Plant shooter) {
        super(type.toString(), 0, x, (int) Math.round(startY));
        this.type = type;
        this.damage = damage;
        this.shooter = shooter;
        this.isDestroyed = false;
        this.exactY = startY;
        this.speedX = speedX;
        this.speedY = speedY;
    }

    @Override
    public void update(GameSession gameSession) {
        if (isDestroyed) return;

        int previousRow = this.y;

        move();

        this.y = (int) Math.round(exactY);
        GameMap map = gameSession.getMap();

        if (this.y < 0 || this.y >= Constants.BOARD_ROWS) {
            this.isDestroyed = true;
            return;
        }

        if (this.y != previousRow) {
            if (previousRow >= 0 && previousRow < Constants.BOARD_ROWS) {
                map.getRow(previousRow).removeProjectile(this);
            }
            map.getRow(this.y).addProjectile(this);
        }

        if (this.x > Constants.BOARD_COLS || this.x < 0) {
            this.isDestroyed = true;
            return;
        }

        List<Zombie> zombiesInRow = map.getRow(this.y).getZombies();

        if (zombiesInRow != null) {
            for (Zombie z : zombiesInRow) {
                if (!z.getHealth().isDead() && this.x >= z.getMovement().getPositionX()) {
                    onHit(z, gameSession);
                    break;
                }
            }
        }
    }

    public void move() {
        this.x += speedX;
        this.exactY += speedY;
    }

    public void onHit(Zombie target, GameSession gameSession) {
        target.getHealth().applyDamage(damage, shooter);

        if (this.type == ProjectileType.ICE_PEA) {
            //TODO: apply snow effect to zombie
        }
        else if (this.type == ProjectileType.MELON) {
            //(Splash Damage)
            applySplashDamage(target, gameSession);
        }

        this.isDestroyed = true;
    }

    private void applySplashDamage(Zombie primaryTarget, GameSession gameSession) {
        GameMap map = gameSession.getMap();

        int splashDamage = this.damage / 2;

        double splashRadiusX = 1.5;

        for (int rowOffset = -1; rowOffset <= 1; rowOffset++) {
            int targetRow = this.y + rowOffset;

            if (targetRow >= 0 && targetRow < Constants.BOARD_ROWS) {
                List<Zombie> zombies = map.getRow(targetRow).getZombies();

                if (zombies != null) {
                    for (Zombie z : zombies) {
                        if (z.getHealth().isDead() || z == primaryTarget) {
                            continue;
                        }
                        double distanceX = Math.abs(z.getMovement().getPositionX() - primaryTarget.getMovement().getPositionX());
                        if (distanceX <= splashRadiusX) {
                            z.getHealth().applyDamage(splashDamage, shooter);
                        }
                    }
                }
            }
        }
    }

    //TODO: remove destroyed projectiles in time system every tick
    public boolean isDestroyed() {
        return isDestroyed;
    }
}