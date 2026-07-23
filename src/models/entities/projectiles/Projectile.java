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
    private boolean isReflectedByJester = false;

    private int bounceCount;

    private double startX;
    //if the projectile doesn't have a maximum range set this field to 0.0
    private double maxRange;


    //splash damage properties
    private int splashDamage = 0;
    private double splashRadiusX = 0.0;
    private int splashRowRadius = 0;

    // status effects carried to the target on hit
    private int chillBonusTicks = 0;   // Snow Pea CHILL_DURATION_EXT
    private int poisonDps = 0;          // Goo Peashooter poison-over-time
    private int poisonDurationTicks = 0;

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

    public void setChillBonusTicks(int ticks) {
        this.chillBonusTicks = ticks;
    }

    public void setPoison(int dps, int durationTicks) {
        this.poisonDps = dps;
        this.poisonDurationTicks = durationTicks;
    }

    // A projectile with vertical speed can finish a tick in a different lane than the row list holding
    // it; CombatSystem.resolveProjectiles re-files those after it has swept every row.
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

        if (handleBlockedPlantCollisions(gameSession)) {
            return;
        }

        handleTerrainCollisions(gameSession);

        // A Jester-reflected projectile is travelling back toward the lawn: it passes over zombies
        // and only strikes plants (handled above in handleBlockedPlantCollisions).
        if (!this.isDestroyed && !this.isReflectedByJester) {
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

        // Iterate a copy. onHit can kill a zombie, and a death effect is free to put new zombies on this
        // very row (a bursting barrel dropping Imps) -- walking the live list would then throw a
        // ConcurrentModificationException mid-flight. A piercing shot keeps looping after a kill, so
        // this is reachable rather than theoretical.
        for (Zombie z : new java.util.ArrayList<>(zombiesInRow)) {
            // isTargetable() also rules out a zombie that has spawned beyond the right edge but not
            // walked on: a pea must fly past where it will appear, not stop dead in mid-air on it.
            if (z.isTargetable() && !hitTargets.contains(z)) {
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
        if (target.getState().isImmuneToFire() && this.element == Element.FIRE) {
            gameSession.reportEvent("The Imp Dragon absorbs a fire projectile at ("
                    + (int) target.getX() + ", " + target.getY() + ") and takes no damage.");
            this.isDestroyed = true;
            return;
        }

        if (target.getState().isSpinning() && this.trajectory != Trajectory.LOBBED) {
            this.speedX = -Math.abs(this.speedX);
            this.isReflectedByJester = true;

            gameSession.reportEvent("The Jester Zombie deflects a projectile back at the plants at ("
                    + (int) target.getX() + ", " + target.getY() + ").");
            return;
        }

        if (target.getState().isSubmerged()) {
            if (this.trajectory != Trajectory.LOBBED) {
                return;
            }
        }

        // Trajectory travels with the hit: a LOBBED melon arcs over whatever the zombie holds in front
        // (newspaper, shoved barrel) and lands on the body behind it.
        target.getHealth().applyDamage(damage, element, shooter, trajectory);
        hitTargets.add(target);
        element.applyOnHit(target.getState());

        if (chillBonusTicks > 0 && element == Element.ICE) {
            target.getState().extendChill(chillBonusTicks);
        }
        if (poisonDps > 0) {
            target.getHealth().applyPoison(poisonDps, poisonDurationTicks);
        }

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
                        // Splash reaches the board, not the queue waiting to walk onto it.
                        if (!z.isTargetable() || z == primaryTarget) {
                            continue;
                        }

                        double distanceX = Math.abs(z.getMovement().getPositionX() - epicenterX);

                        if (distanceX <= splashRadiusX) {
                            // Splash rains down from the same arc as the shot that caused it, so it
                            // clears front shields on the neighbours too.
                            z.getHealth().applyDamage(this.splashDamage, this.element, this.shooter,
                                    this.trajectory);
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

    // Destroyed projectiles are retired by CombatSystem.resolveProjectiles, which drops them from the
    // row on the same tick they are flagged.
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

    private boolean handleBlockedPlantCollisions(GameSession gameSession) {
        if (this.trajectory == Trajectory.LOBBED) {
            return false;
        }

        int currentCellIndex = (int) this.x;
        if (currentCellIndex < 0 || currentCellIndex >= 9) return false;

        Cell currentCell = gameSession.getMap().getRow(this.y).cellAt(currentCellIndex);

        if (currentCell != null && currentCell.hasPlant()) {
            Plant p = currentCell.getCurrentPlant();
            if (p != null && (p.isFrozen() || p.hasOctopus())) {

                if (p.hasOctopus()) {
                    p.damageOctopus(this.damage);
                } else if (p.isFrozen()) {
                    // A fire projectile shatters the ice outright; anything else chips its 600 HP.
                    p.damageIceBlock(this.damage, this.element);
                }

                this.isDestroyed = true;
                return true;
            }

            // A Jester-reflected projectile flies back toward the lawn and strikes the player's own
            // plant -- carrying its element with it, so a Torchwood-lit pea burns and an ice pea chills.
            if (p != null && !p.isDead() && this.isReflectedByJester) {
                p.takeElementalHit(this.damage, this.element);
                this.isDestroyed = true;
                return true;
            }
        }

        return false;
    }

    public boolean isReflectedByJester() {
        return isReflectedByJester;
    }

    public void setReflectedByJester(boolean reflectedByJester) {
        this.isReflectedByJester = reflectedByJester;
    }
}