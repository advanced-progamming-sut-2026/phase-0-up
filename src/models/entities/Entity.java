package models.entities;

import models.game.GameSession;

//every entity should extend this class and have update function to get updated every tick
public abstract class Entity {
    protected String name;
    protected int id;
    protected double x;
    protected int y;

    public Entity(String name, int id, double x, int y) {
        this.name = name;
        this.id = id;
        this.x = x;
        this.y = y;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getId() {
        return this.id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public double getX() {
        return this.x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public int getY() {
        return this.y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public abstract void update(GameSession gameSession);
}
