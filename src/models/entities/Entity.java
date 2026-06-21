package models.entities;
//every entity should extend this class and have update function to get updated every tick
public abstract class Entity {
    protected String name;
    protected int id;
    protected double x;
    protected int y;
    protected int hp;
    protected boolean isAlive;

    public String getName(){return name;}
    public int getId() {return 0;}
    public void setId(int id) {};
    public double getX() {return 0;}
    public void setX(double x) {}
    public int getY() {return 0;}
    public void setY(int y) {};
    public int getHp() {return 0;}
    public void setHp(int hp) {}
    public boolean isAlive() {return false;}
    public void setAlive(boolean alive) {}
}
