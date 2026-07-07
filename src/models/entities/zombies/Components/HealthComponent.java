package models.entities.zombies.Components;

import models.entities.plants.Plant;

import java.util.List;
import java.util.Stack;

public class HealthComponent {
    private Stack<HealthLayer> layers = new Stack<>();
    private List<ArmorType> armorTypes;

    public HealthComponent(List<ArmorType> armorTypes) {
        this.armorTypes = armorTypes;
        for(ArmorType a : armorTypes){
            addLayer(new HealthLayer(a));
        }
    }

    public boolean isDead() {
        return layers.isEmpty() || getTotalHP() <= 0;
    }

    public void addLayer(HealthLayer layer) {
        layers.push(layer);
    }

    public void applyDamage(int damage, Plant attacker) {
        int remainingDamage = damage;
        while (remainingDamage > 0 && !layers.isEmpty()){
            HealthLayer topLayer = layers.peek();
            int absorbed = topLayer.takeDamage(remainingDamage);
            remainingDamage -= absorbed;

            if(topLayer.getCurrentHp() <= 0){
                layers.pop();
            }
        }

        if (isDead()){
            die();
        }
    }
    public boolean tryRemoveMetallicArmor() {
        if(layers.peek().getType() == ArmorType.BUCKET){
            layers.pop(); return true;
        } else if(layers.peek().getType() == ArmorType.SHOULDER_ARMOR || layers.peek().getType() == ArmorType.CROWN){
            layers.pop();
            if(layers.peek().getType() != ArmorType.BASE_BODY){
                layers.pop();
            }
            return true;
        }
        return false;
    }
    private void applyChillEffectToAttacker(Plant attacker) {}
    private void die() {

    }

    public int getTotalHP(){
        int totalHP = 0;
        for(HealthLayer h : this.layers){
            totalHP += h.getCurrentHp();
        }
        return totalHP;
    }

    //TODO: apply poison overtime damage
}
