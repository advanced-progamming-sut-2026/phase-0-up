package models.entities.zombies.Components;

import models.entities.plants.Plant;
import models.entities.projectiles.DamageType;

import java.util.Stack;

public class HealthComponent {
    private Stack<HealthLayer> layers;

    public HealthComponent() {
        layers = new Stack<>();
    }

    public boolean isDead() {
        return layers.isEmpty() || getTotalHP() <= 0;
    }

    public void addLayer(HealthLayer layer) {
        layers.push(layer);
    }

    public void applyDamage(int damage, DamageType damageType, Plant attacker) {
        if (isDead()) return;

        int remainingDamage = damage;

        if (damageType == DamageType.POISON) {
            for (HealthLayer layer : layers) {
                if (layer.getType() == ArmorType.BASE_BODY) {
                    layer.takeDamage(remainingDamage);
                    break;
                }
            }
        }
        else {
            while (remainingDamage > 0 && !layers.isEmpty()) {
                HealthLayer topLayer = layers.peek();
                int absorbed = topLayer.takeDamage(remainingDamage);
                remainingDamage -= absorbed;
            }
        }

        //TODO: if damage type is overhead front shields should be ignored


        layers.removeIf(layer -> layer.getCurrentHp() <= 0);

        if (isDead()) {
            die();
        }
    }
    public boolean tryRemoveMetallicArmor() {return false;}
    private void applyChillEffectToAttacker(Plant attacker) {}
    private void die() {}

    public int getTotalHP(){
        int totalHP = 0;
        for(HealthLayer h : this.layers){
            totalHP += h.getCurrentHp();
        }
        return totalHP;
    }

    //TODO: apply poison overtime damage
}
