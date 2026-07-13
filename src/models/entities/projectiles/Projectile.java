package models.entities.projectiles;

import models.entities.Entity;
import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import models.map.Cell;
import models.map.GameMap;
import models.map.Row;
import models.map.Terrains.Terrain;
import utils.Constants;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class Projectile extends Entity {
    private Plant shooter;
    private ProjectileType type;
    private int damage;
    private Element element;
    private Trajectory trajectory;

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

    private Set<Terrain> hitTerrains;

    public Projectile(double x, double startY, ProjectileType type, int damage,
                      double speedX, double speedY, Plant shooter, double maxRange,
                      Element element, Trajectory trajectory) {
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
        this.hitTerrains = new HashSet<>();

        this.maxRange = maxRange;
        this.startX = x;
        this.element = element;
        this.trajectory = trajectory;
    }

    public void setSplashProperties(int splashDamage, double splashRadiusX, int splashRowRadius) {
        this.splashDamage = splashDamage;
        this.splashRadiusX = splashRadiusX;
        this.splashRowRadius = splashRowRadius;
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

        if (checkOutOfBounds()) return;

        handleTerrainCollisions(gameSession);

        if (!this.isDestroyed) {
            handleZombieCollisions(gameSession);
        }
    }

    private boolean checkOutOfBounds() {
        if (this.y < 0 || this.y >= Constants.BOARD_ROWS) {
            if (this.type == ProjectileType.BOWLING_BULB) {
                this.y = (this.y < 0) ? 0 : Constants.BOARD_ROWS - 1;
                this.exactY = this.y;
                this.speedY = -this.speedY;
            } else {
                this.isDestroyed = true;
                return true;
            }
        }

        if (this.x > Constants.BOARD_COLS || this.x < 0) {
            this.isDestroyed = true;
            return true;
        }
        return false;
    }

    private void handleTerrainCollisions(GameSession gameSession) {
        int currentCellIndex = (int) this.x;
        if (currentCellIndex < 0 || currentCellIndex >= 9) return;

        Cell currentCell = gameSession.getMap().getRow(this.y).cellAt(currentCellIndex);
        currentCell.interactWithProjectile(this);

        if (this.trajectory == Trajectory.LOBBED) return;

        Iterator<Terrain> iterator = currentCell.getTerrain().iterator();
        while (iterator.hasNext()) {
            Terrain t = iterator.next();
            if (t.doesBlockProjectiles() && this.x >= (currentCellIndex + 0.5) && !hitTerrains.contains(t)) {
                t.takeDamage(this.damage, this.element);
                hitTerrains.add(t);

                if (t.isDestroyed()) {
                    iterator.remove();
                }

                if (this.type == ProjectileType.BOWLING_BULB && this.bounceCount > 0) {
                    performBounce();
                    return;
                }

                if (this.pierceCount > 0) {
                    this.pierceCount--;
                } else {
                    this.isDestroyed = true;
                    return;
                }
            }
        }
    }

    private void handleZombieCollisions(GameSession gameSession) {
        List<Zombie> zombiesInRow = gameSession.getMap().getRow(this.y).getZombies();
        if (zombiesInRow == null) return;

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

    public void move() {
        this.x += speedX;
        this.exactY += speedY;
    }

    public void onHit(Zombie target, GameSession gameSession) {
        target.getHealth().applyDamage(damage, element, shooter);

        hitTargets.add(target);

        element.applyOnHit(target.getState());

        if (this.type == ProjectileType.BOWLING_BULB && bounceCount > 0) {
            performBounce();
            return;
        }

        if (this.splashDamage > 0) {
            applySplashDamage(target, gameSession);
        }

        if (this.pierceCount > 0){
            pierceCount--;
            return;
        }

        this.isDestroyed = true;
    }

    private void applySplashDamage(Zombie primaryTarget, GameSession gameSession) {
        if (this.splashDamage <= 0) return;

        GameMap map = gameSession.getMap();

        double epicenterX = primaryTarget.getMovement().getPositionX();

        for (int rowOffset = -splashRowRadius; rowOffset <= splashRowRadius; rowOffset++) {
            int targetRow = this.y + rowOffset;

            if (targetRow >= 0 && targetRow < utils.Constants.BOARD_ROWS) {
                Row currentRow = map.getRow(targetRow);

                List<Zombie> zombies = currentRow.getZombies();
                if (zombies != null) {
                    for (Zombie z : zombies) {
                        if (z.getHealth().isDead() || z == primaryTarget) {
                            continue;
                        }

                        double distanceX = Math.abs(z.getMovement().getPositionX() - epicenterX);

                        if (distanceX <= splashRadiusX) {
                            z.getHealth().applyDamage(this.splashDamage, this.element, this.shooter);
                            this.element.applyOnHit(z.getState());
                        }
                    }
                }

                for (int col = 0; col < 9; col++) {
                    Cell cell = currentRow.cellAt(col);

                    double distanceX = Math.abs(cell.getX() + 0.5 - epicenterX);

                    if (distanceX <= splashRadiusX) {
                        List<Terrain> terrains = cell.getTerrain();

                        Iterator<Terrain> iterator = terrains.iterator();
                        while (iterator.hasNext()) {
                            Terrain t = iterator.next();

                            if (t.doesBlockProjectiles()) {
                                t.takeDamage(this.splashDamage, this.element);

                                if (t.isDestroyed()) {
                                    iterator.remove();
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void performBounce() {
        this.bounceCount--;

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
    }

    //TODO: remove destroyed projectiles in time system every tick
    public boolean isDestroyed() {
        return isDestroyed;
    }

    public void destroy() { this.isDestroyed = true;}

    public void setBounceCount(int bounceCount) {
        this.bounceCount = bounceCount;
    }

    public void setPierceCount(int pierceCount) {
        this.pierceCount = pierceCount;
    }

    public Element getElement() {
        return element;
    }

    public void setElement(Element element) {
        this.element = element;
    }

    public Trajectory getTrajectory() {
        return trajectory;
    }

    public void setDamage(int damage) {
        this.damage = damage;
    }

    public int getDamage() {
        return damage;
    }

    public Plant getShooter() {
        return shooter;
    }
}