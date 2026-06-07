package models.entities.zombies.Abilities;

import models.entities.zombies.Zombie;

public class KillPlantsAbility implements ZombieAbility{
    private boolean isExplorer;
    private boolean isTorchLight;

    public void setExplorer(boolean explorer) {}
    public void setTorchLight(boolean torchLight) {}
    public boolean isExplorer() {return isExplorer;}
    public boolean isTorchLight() {return isTorchLight;}

    @Override
    public void execute(Zombie zombie) {}
}
