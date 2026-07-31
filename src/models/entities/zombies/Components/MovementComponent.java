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

    // Queues a hop; CombatSystem re-files the Row. An off-board lane is refused (callers use y+-1).
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
        // A shove, not a step, so it beats the can-it-move check: Garlic repels the zombie BITING it.
        if (isSwitchingLane && canBeShoved()) {
            this.y = this.targetY;
            this.isSwitchingLane = false;
            return;
        }
        if (state.isUnableToMove()) return;
        double currentSpeed = (state.isChilled() ? this.speed * 0.5 : this.speed)
                * utils.Constants.ZOMBIE_SPEED_SCALE;   // global speed knob
        if(!state.isHypnotized()) {
            this.x -= currentSpeed;
        } else {
            this.x += currentSpeed;
        }
    }

    private boolean canBeShoved() {
        return !state.isFrozen() && !state.isButtered()
                && state.getCurrentAction() != ActionState.DYING;
    }
    public double getPositionX() { return x; }
    public int getPositionY() { return y; }
    public boolean isSwitchingLane() { return isSwitchingLane; }

    public void setPositionX(double x) {
        this.x = x;
    }
}