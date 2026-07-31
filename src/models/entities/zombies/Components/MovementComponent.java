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

    // Queues a hop into another lane; move() performs it on the next tick and CombatSystem then re-files
    // the zombie into the Row that matches its new y.
    //
    // A target off the top or bottom of the lawn is refused outright. Callers reach for a neighbouring
    // lane without checking the edges (a slider tile on row 0 asks for row -1 every tick it is stood on),
    // and letting that through would strand the zombie on a lane index no Row owns -- it would be dropped
    // from the board entirely by the re-filing pass.
    public void startLaneSwitch(int newLaneY) {
        if (newLaneY < 0 || newLaneY >= utils.Constants.BOARD_ROWS) {
            return;
        }
        if (!isSwitchingLane && this.y != newLaneY) {
            this.targetY = newLaneY;
            this.isSwitchingLane = true;
        }
    }

    public void move() {
        if (state.isUnableToMove()) return;
        double currentSpeed = (state.isChilled() ? this.speed * 0.5 : this.speed)
                * utils.Constants.ZOMBIE_SPEED_SCALE;   // global speed knob
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

    public void setPositionX(double x) {
        this.x = x;
    }
}