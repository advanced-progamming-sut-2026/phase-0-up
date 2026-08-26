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

    // Which way this zombie is actually travelling, by exactly the arithmetic move() uses above.
    //
    // Two things flip a zombie: being hypnotised (the branch in move()) and having its SPEED negated,
    // which is what the Prospector's dynamite does to send it back across the lawn. Asking
    // isHypnotized() alone therefore answers the wrong question for a blasted Prospector -- it is
    // walking right and is not charmed -- and the view was drawing it, and planting its feet, as though
    // it were still heading for the house.
    //
    // Derived rather than stored so it can never disagree with move(); a stored flag would be a second
    // opinion to keep in sync with the one line that actually moves the zombie.
    public boolean isMovingRight() {
        return state.isHypnotized() ? speed > 0 : speed < 0;
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