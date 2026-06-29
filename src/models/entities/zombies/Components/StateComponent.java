package models.entities.zombies.Components;

public class StateComponent {
    private boolean isFlying = false;
    private boolean isSubmerged = false;
    private boolean isFrozen = false;
    private boolean isStopped = false;

    public void setFlying(boolean flying) {
        isFlying = flying;
    }
    public void setSubmerged(boolean submerged) {
        isSubmerged = submerged;
    }
    public void setFrozen(boolean frozen) {
        isFrozen = frozen;
    }
    public void setStopped(boolean stopped) {
        isStopped = stopped;
    }
    public boolean isFlying() {return isFlying;}
    public boolean isSubmerged() {return isSubmerged;}
    public boolean isFrozen() {return isFrozen;}
    public boolean isStopped(){return isStopped;}
}
