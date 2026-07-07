package models.entities.projectiles;

import models.entities.Entity;
import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import models.map.GameMap;
import utils.Constants;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Projectile extends Entity {
    private Plant shooter;
    private ProjectileType type;
    private int damage;
    private DamageType damageType;

    //for piercing projectiles
    private int pierceCount;
    private Set<Zombie> hitTargets;

    private double speedX;
    private double speedY;
    private double exactY;

    private boolean isDestroyed;

    private int bounceCount;

    private double startX;
    //if the projectile doesn't have a maximum range set this field to 0.0
    private double maxRange;


    //splash damage properties
    private int splashDamage = 0;
    private double splashRadiusX = 0.0;
    private int splashRowRadius = 0;
    private boolean appliesSlowEffect = false;

    public Projectile(double x, double startY, ProjectileType type, int damage,
                      double speedX, double speedY, Plant shooter, double maxRange, DamageType damageType) {
        super(type.toString(), 0, x, (int) Math.round(startY));
        this.type = type;
        this.damage = damage;
        this.shooter = shooter;
        this.isDestroyed = false;
        this.exactY = startY;
        this.speedX = speedX;
        this.speedY = speedY;
        this.pierceCount = 0;
        this.hitTargets = new HashSet<>();

        this.maxRange = maxRange;
        this.startX = x;
        this.damageType = damageType;
    }

    public void setSplashProperties(int splashDamage, double splashRadiusX, int splashRowRadius, boolean appliesSlowEffect) {
        this.splashDamage = splashDamage;
        this.splashRadiusX = splashRadiusX;
        this.splashRowRadius = splashRowRadius;
        this.appliesSlowEffect = appliesSlowEffect;
    }

    //TODO: in game engine after finishing the loop on the projectiles check if any of them changed line
    @Override
    public void update(GameSession gameSession) {
        if (isDestroyed) return;

        if (maxRange > 0.0 && Math.abs(this.x - this.startX) >= maxRange) {
            this.isDestroyed = true;
            return;
        }

        move();

        this.y = (int) Math.round(exactY);
        GameMap map = gameSession.getMap();

        if (this.y < 0 || this.y >= Constants.BOARD_ROWS) {

            if (this.type == ProjectileType.BOWLING_BULB) {
                if (this.y < 0) {
                    this.y = 0;
                    this.exactY = 0;
                } else {
                    this.y = Constants.BOARD_ROWS - 1;
                    this.exactY = this.y;
                }
                this.speedY = -this.speedY;
            }
            else {
                this.isDestroyed = true;
                return;
            }
        }

        if (this.x > Constants.BOARD_COLS || this.x < 0) {
            this.isDestroyed = true;
            return;
        }

        List<Zombie> zombiesInRow = map.getRow(this.y).getZombies();

        if (zombiesInRow != null) {
            double previousX = this.x - speedX;

            for (Zombie z : zombiesInRow) {
                if (!z.getHealth().isDead() && !hitTargets.contains(z)) {
                    double zombieX = z.getMovement().getPositionX();

                    boolean hitMovingRight = (speedX > 0 && previousX <= zombieX && this.x >= zombieX);
                    boolean hitMovingLeft  = (speedX < 0 && previousX >= zombieX && this.x <= zombieX);

                    boolean hitStationary = (speedX == 0 && Math.abs(this.x - zombieX) <= 0.5);

                    if (hitMovingRight || hitMovingLeft || hitStationary) {
                        onHit(z, gameSession);
                        if (this.isDestroyed) break;
                    }
                }
            }
        }
    }

    public void move() {
        this.x += speedX;
        this.exactY += speedY;
    }

    public void onHit(Zombie target, GameSession gameSession) {
        target.getHealth().applyDamage(damage, damageType,shooter);

        hitTargets.add(target);

        if (this.pierceCount > 0){
            pierceCount--;
            return;
        }

        if (this.type == ProjectileType.ICE_PEA) {
            //TODO: apply slow effect to zombie
        } else if (this.type == ProjectileType.MELON) {
            //(Splash Damage)
            applySplashDamage(target, gameSession);
        } else if (this.type == ProjectileType.BOWLING_BULB){
            if (bounceCount > 0) {
                bounceCount--;

                boolean canGoUp = (this.y > 0);
                boolean canGoDown = (this.y < Constants.BOARD_ROWS - 1);

                int direction = 0;
                if (canGoUp && canGoDown) {
                    direction = Math.random() > 0.5 ? -1 : 1;
                } else if (canGoUp) {
                    direction = -1;
                } else if (canGoDown) {
                    direction = 1;
                }

                this.speedY = direction * 0.5;

                return;
            }
        } else if (this.type == ProjectileType.BUTTER){
            //TODO: apply stun to target
        }

        this.isDestroyed = true;
    }

    private void applySplashDamage(Zombie primaryTarget, GameSession gameSession) {
        if (this.splashDamage <= 0) return;

        GameMap map = gameSession.getMap();

        for (int rowOffset = -splashRowRadius; rowOffset <= splashRowRadius; rowOffset++) {
            int targetRow = this.y + rowOffset;

            if (targetRow >= 0 && targetRow < utils.Constants.BOARD_ROWS) {
                List<Zombie> zombies = map.getRow(targetRow).getZombies();

                if (zombies != null) {
                    for (Zombie z : zombies) {
                        if (z.getHealth().isDead() || z == primaryTarget) {
                            continue;
                        }

                        double distanceX = Math.abs(z.getMovement().getPositionX() - primaryTarget.getMovement().getPositionX());

                        if (distanceX <= splashRadiusX) {
                            z.getHealth().applyDamage(this.splashDamage, this.damageType, this.shooter);

                            if (this.appliesSlowEffect && z.getMovement() != null) {
                                //TODO: apply slow effect to zombie
                            }
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

    public void setBounceCount(int bounceCount) {
        this.bounceCount = bounceCount;
    }

    public void setPierceCount(int pierceCount) {
        this.pierceCount = pierceCount;
    }

    public Plant getShooter() {
        return shooter;
    }
}