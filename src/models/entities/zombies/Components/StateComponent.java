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
    private float frozenTimer = 0f;
    private float chilledTimer = 0f;
    private float butteredTimer = 0f;
    private boolean isTorchLit = false;

    public void update(float deltaTime) {
        if (frozenTimer > 0) frozenTimer -= deltaTime;
        if (chilledTimer > 0) chilledTimer -= deltaTime;
        if (butteredTimer > 0) butteredTimer -= deltaTime;
    }

    public void applyFreeze(float duration) {
        this.frozenTimer = duration;
    }

    public void applyChill(float duration) {
        this.chilledTimer = duration;
    }

    public void applyButter(float duration) {
        this.butteredTimer = duration;
    }
    public ActionState getCurrentAction() { return currentAction; }
    public void setAction(ActionState action) { this.currentAction = action; }

    public boolean isUnableToMove() {
        return isFrozen() || isButtered() || currentAction == ActionState.EATING || currentAction == ActionState.DYING;
    }

    public boolean isFrozen() { return frozenTimer > 0; }
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

    public boolean isTorchLit() { return isTorchLit; }
    public void setTorchLit(boolean torchLit) { this.isTorchLit = torchLit; }
}