package models.map;

import models.entities.plants.Plant;
import models.entities.projectiles.Projectile;
import models.entities.zombies.Components.ArmorType;
import models.entities.zombies.Zombie;
import utils.Constants;
import utils.Result;

import java.lang.module.ResolutionException;
import java.util.ArrayList;
import java.util.List;

public class Row {
    private List<Cell> cells;
    private int index;
    private List<Zombie> activeZombies;
    private List<Projectile> activeProjectiles;
    private Lawnmower lawnmower;
    private List<Obstacle> activeObstacles;

    public Row(int index) {
        cells = new ArrayList<>();
        for(int i = 0 ; i < 9; i++){
            Cell e = new Cell(i + 0.5 , index , true);
            cells.add(e);
        }
        activeProjectiles = new ArrayList<>();
        activeZombies = new ArrayList<>();
        lawnmower = new Lawnmower(index);
        this.index = index;
        this.activeObstacles = new ArrayList<>();
    }

    public Cell cellAt(int x){
        return cells.get(x);
    }

    public Lawnmower getLawnmower() {
        return lawnmower;
    }

    public void setLawnmower(Lawnmower lawnmower) {
        this.lawnmower = lawnmower;
    }

    public Plant frontPlant(){
        for(int i = cells.size() - 1; i >= 0; i--){
            Cell cell = cells.get(i);
            if(cell.hasPlant()){
                return cell.getCurrentPlant();
            }
        }
        return null;
    }


    public Zombie frontZombie(){
        if (!hasZombie()) return null;
        Zombie frontZombie = activeZombies.getFirst();
        for (Zombie z : activeZombies){
            if (z.getMovement().getPositionX() < frontZombie.getMovement().getPositionX()){
                frontZombie = z;
            }
        }
        return frontZombie;
    }

    public void addProjectile(Projectile p){
        activeProjectiles.add(p);
    }

    public boolean hasZombie(){
        return !activeZombies.isEmpty();
    }

    public List<Zombie> getZombies() {
        return activeZombies;
    }

    public List<Projectile> getActiveProjectiles() {
        return activeProjectiles;
    }

    public List<Cell> getCells() {
        return cells;
    }

    public int getIndex() {
        return index;
    }

    public void removeProjectile(Projectile p){
        activeProjectiles.remove(p);
    }

    public void addObstacle(Obstacle o) {
        activeObstacles.add(o);
    }

    public void removeObstacle(Obstacle o) {
        activeObstacles.remove(o);
    }

    public List<Obstacle> getActiveObstacles() {
        return activeObstacles;
    }

    public Obstacle frontObstacle() {
        if (activeObstacles.isEmpty()) return null;

        Obstacle front = activeObstacles.getFirst();
        for (Obstacle o : activeObstacles) {
            if (!o.isDestroyed() && o.getX() < front.getX()) {
                front = o;
            }
        }
        return front;
    }
}
