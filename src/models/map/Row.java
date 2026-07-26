package models.map;

import models.entities.plants.Plant;
import models.entities.projectiles.Projectile;
import models.entities.zombies.Zombie;

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

    // The plant a zombie walking down this row meets first: the rightmost one still standing.
    //
    // Skips plants that are dead but not yet swept up. A plant stays in its cell until the end of the
    // tick it died in, and returning it would have a zombie stop to eat a corpse while the live plant
    // behind it went untouched.
    public Plant frontPlant(){
        for(int i = cells.size() - 1; i >= 0; i--){
            Cell cell = cells.get(i);
            if(cell.hasPlant() && !cell.getCurrentPlant().isDead()){
                return cell.getCurrentPlant();
            }
        }
        return null;
    }


    // The zombie furthest along this row -- the natural target for anything shooting down it.
    //
    // Only counts zombies that are alive and on the grid. Without that filter the leftmost entry wins
    // outright, so a corpse still awaiting cleanup (or one that breached past the house) would shadow
    // the live zombie actually leading the charge, and a lone zombie still walking on from the right
    // edge would be reported as "in front" before it had arrived.
    public Zombie frontZombie(){
        Zombie front = null;
        for (Zombie z : activeZombies){
            if (!z.isTargetable()){
                continue;
            }
            if (front == null || z.getMovement().getPositionX() < front.getMovement().getPositionX()){
                front = z;
            }
        }
        return front;
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
