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

    public Row(int index) {
        cells = new ArrayList<>();
        for(int i = 0 ; i < 9; i++){
            boolean isPlantable = false;
            if(i < 4) isPlantable = true;
            Cell e = new Cell(i , index , isPlantable);
            cells.add(e);
        }
        activeProjectiles = new ArrayList<>();
        activeZombies = new ArrayList<>();
        lawnmower = new Lawnmower(index);
        this.index = index;
    }

    public Cell cellAt(int x){return cells.get(x);}

    public Lawnmower getLawnmower() {
        return lawnmower;
    }

    public void setLawnmower(Lawnmower lawnmower) {
        this.lawnmower = lawnmower;
    }

    public Plant frontPlant(){return null;}

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

}
