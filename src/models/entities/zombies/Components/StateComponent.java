package models.entities.zombies.Components;

public class StateComponent {
    public enum ActionState {
        WALKING,
        EATING,
        LANE_SWITCHING,
        DYING,
        IDLE
    }

    private ActionState currentAction = ActionState.WALKING;

    private boolean isFlying = false;
    private boolean isSubmerged = false;
    private boolean isDecapitated = false;
    private boolean isHypnotized = false;
    private int frozenTimer = 0;
    private int chilledTimer = 0;
    private int butteredTimer = 0;
    private boolean isTorchLit = false;

    private boolean isPermanentlyFrozen = false;

    public void update() {
        if (frozenTimer > 0 &&!isPermanentlyFrozen) frozenTimer--;
        if (chilledTimer > 0) chilledTimer--;
        if (butteredTimer > 0) butteredTimer--;
    }

    public void applyFreeze(int durationInTicks) {
        this.frozenTimer = durationInTicks;
    }

    public void applyChill(int durationInTicks) {
        this.chilledTimer = durationInTicks;
    }

    public void applyButter(int durationInTicks) {
        this.butteredTimer = durationInTicks;
    }
    public ActionState getCurrentAction() { return currentAction; }
    public void setAction(ActionState action) { this.currentAction = action; }

    public boolean isUnableToMove() {
        return isFrozen() || isButtered() || currentAction == ActionState.EATING || currentAction == ActionState.DYING;
    }

    public boolean isChilled() { return chilledTimer > 0 && !isFrozen(); }
    public boolean isButtered() { return butteredTimer > 0; }

    public boolean isFlying() { return isFlying; }
    public void setFlying(boolean flying) { isFlying = flying; }

    public boolean isSubmerged() { return isSubmerged; }
    public void setSubmerged(boolean submerged) { isSubmerged = submerged; }

    public boolean isDecapitated() { return isDecapitated; }
    public void setDecapitated(boolean decapitated) { isDecapitated = decapitated; }

    public boolean isHypnotized() { return isHypnotized; }
    public void setHypnotized(boolean hypnotized) { isHypnotized = hypnotized; }

    public void setFrozen(boolean frozen) {
        this.isPermanentlyFrozen = frozen;
        if (!frozen) {
            this.frozenTimer = 0;
        }
    }

    public boolean isFrozen() {
        return isPermanentlyFrozen || frozenTimer > 0;
    }


    public boolean isTorchLit() { return isTorchLit; }
    public void setTorchLit(boolean torchLit) { this.isTorchLit = torchLit; }
}