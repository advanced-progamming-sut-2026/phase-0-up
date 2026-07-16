package models.entities.zombies.Components;

public class StateComponent {
    private ActionState currentAction = ActionState.WALKING;
    private boolean isReadyForLaser = false;
    private boolean isFlying = false;
    private boolean isSubmerged = false;
    private boolean isDecapitated = false;
    private boolean isHypnotized = false;
    private int frozenTimer = 0;
    private int chilledTimer = 0;
    private int butteredTimer = 0;
    private boolean isTorchLit = false;
    private boolean isSpinning = false;
    private boolean isImmuneToFire = false;

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

    // Snow Pea's CHILL_DURATION_EXT upgrade lengthens the chill already applied by the ice hit.
    public void extendChill(int extraTicks) {
        this.chilledTimer += extraTicks;
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
    public boolean isReadyForLaser() {
        return isReadyForLaser;
    }

    public void setReadyForLaser(boolean readyForLaser) {
        this.isReadyForLaser = readyForLaser;
    }

    public void setFrozenTimer(int v) {
        this.frozenTimer = v;
    }

    public void setChilledTimer(int i) {
        this.chilledTimer = i;
    }

    public boolean isSpinning() { return isSpinning; }
    public void setSpinning(boolean spinning) { isSpinning = spinning; }
    public boolean isImmuneToFire() { return isImmuneToFire; }
    public void setImmuneToFire(boolean immuneToFire) { isImmuneToFire = immuneToFire; }
}