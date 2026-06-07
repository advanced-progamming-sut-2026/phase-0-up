package models.entities.plants;

public abstract class Entity {
    protected int id;
    protected int x;
    protected int y;
    protected int hp;
    protected boolean isAlive;

    public int getId() {return 0;}
    public void setId(int id) {};
    public int getX() {return 0;}
    public void setX(int x) {};
    public int getY() {return 0;}
    public void setY(int y) {};
    public int getHp() {return 0;}
    public void setHp(int hp) {};
    public boolean isAlive() {return false;}
    public void setAlive(boolean alive) {};
}
