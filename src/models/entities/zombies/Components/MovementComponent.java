package models.entities.zombies.Components;

public class MovementComponent {
    private double speed;
    private double x;
    private int y;
    private StateComponent state;
    private int targetY;
    private boolean isSwitchingLane = false;

    public MovementComponent(double speed, double x, int y, StateComponent state) {
        this.speed = speed;
        this.x = x;
        this.y = y;
        this.targetY = y;
        this.state = state;
    }

    public void setSpeed(double speed) { this.speed = speed; }

    public double getSpeed() {
        return speed;
    }

    public void startLaneSwitch(int newLaneY) {
        if (!isSwitchingLane && this.y != newLaneY) {
            this.targetY = newLaneY;
            this.isSwitchingLane = true;
        }
    }

    public void move() {
        if (state.isUnableToMove()) return;
        double currentSpeed = state.isChilled() ? this.speed * 0.5 : this.speed;
        if (isSwitchingLane) {
            this.y = this.targetY;
            this.isSwitchingLane = false;
            return;
        }
        if(!state.isHypnotized()) {
            this.x -= currentSpeed;
        } else {
            this.x += currentSpeed;
        }
    }

    public double getPositionX() { return x; }
    public int getPositionY() { return y; }
    public boolean isSwitchingLane() { return isSwitchingLane; }
    //TODO: this class needs an update method which gets called on every tick

    public void setPositionX(double x) {
        this.x = x;
    }
}