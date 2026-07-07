package models.entities.zombies.Components;

public class MovementComponent {
    private double speed;
    private double x;
    private int y;

    public MovementComponent(double speed, double startPosition , int y) {}
    public void move(StateComponent state , double x , int y) {}
    public double getPositionX() { return x; }
    public int getPositionY() {return y;}


    //TODO: this class needs an update method
}
