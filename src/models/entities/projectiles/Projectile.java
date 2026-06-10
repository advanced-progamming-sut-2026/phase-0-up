package models.entities.projectiles;


import models.entities.zombies.Zombie;

public class Projectile {
    private ProjectileType type;
    private int damage;
    private double x;
    private int lane;

    public void move(){};
    public void onHit(Zombie z){};
}
