package models.entities.zombies.Components;

import models.entities.plants.Plant;

import java.util.Stack;

public class HealthComponent {
    private Stack<HealthLayer> layers = new Stack<>();

    public boolean isDead() {return false;}
    public void addLayer(HealthLayer layer) {}
    public void applyDamage(int damage, Plant attacker) {}
    public boolean tryRemoveMetallicArmor() {return false;}
    private void applyChillEffectToAttacker(Plant attacker) {}
    private void die() {}
}
