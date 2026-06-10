package models.entities.projectiles;


import models.entities.Entity;
import models.entities.zombies.Zombie;

public class Projectile extends Entity {
    private ProjectileType type;
    private int damage;
    private double x;
    private double y;
    private int speed;

    public void move(){};
    public void onHit(Zombie z){};

    @Override
    public void update() {}
}
