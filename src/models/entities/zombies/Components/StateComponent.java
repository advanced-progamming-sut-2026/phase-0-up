package models.entities.zombies.Components;

public class StateComponent {
    private boolean isFlying = false;
    private boolean isSubmerged = false;
    private boolean isFrozen = false;
    private boolean isStopped = false;

    public void freeze() {}
    public void submerge(){}
    public void fly(){}
    public boolean isFlying() {return isFlying;}
    public boolean isSubmerged() {return isSubmerged;}
    public boolean isFrozen() {return isFrozen;}
    public boolean isStopped(){return isStopped;}
}
