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
    private double speed;

    private boolean isDestroyed;

    public Projectile(double x, int y, ProjectileType type, int damage, double speed, Plant shooter) {
        super(type.toString(), 0, x, y); // اگر id صفر است، بهتر است بعدا سیستم id ساز بسازید
        this.type = type;
        this.damage = damage;
        this.speed = speed;
        this.shooter = shooter;
        this.isDestroyed = false;
    }

    @Override
    public void update(GameSession gameSession) {
        if (isDestroyed) return;

        move();

        if (this.x > Constants.BOARD_COLS) {
            this.isDestroyed = true;
            return;
        }

        GameMap map = gameSession.getMap();
        List<Zombie> zombiesInRow = map.getRow(y).getZombies();

        if (zombiesInRow != null) {
            for (Zombie z : zombiesInRow) {
                if (!z.getHealth().isDead() && this.x >= z.getMovement().getPositionX()) {
                    onHit(z);
                    break;
                }
            }
        }
    }

    public void move() {
        this.x += speed;
    }

    public void onHit(Zombie z) {
        z.getHealth().applyDamage(damage, shooter);

        if (this.type == ProjectileType.ICE_PEA) {
            //apply freeze
        } else if (this.type == ProjectileType.MELON) {
            //apply splash group damage
        }

        this.isDestroyed = true;
    }

    //TODO: this method should be called by game engine and system to remove destroyed projectiles
    public boolean isDestroyed() {
        return isDestroyed;
    }
}