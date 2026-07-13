package models.entities.zombies.Components;

import models.entities.plants.Plant;
import models.entities.projectiles.Element;

import java.util.List;
import java.util.Stack;

public class HealthComponent {
    private Stack<HealthLayer> layers;
    private List<ArmorType> armorTypes;

    public HealthComponent(List<ArmorType> armorTypes) {
        layers = new Stack<>();
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

    public void applyDamage(int damage, Element element, Plant attacker) {
        if (isDead()) return;

        int remainingDamage = damage;

        if (element.piercesBaseArmor()) {
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

        //TODO: lobbed (overhead) delivery should ignore front shields; pass Trajectory here when needed


        layers.removeIf(layer -> layer.getCurrentHp() <= 0);

        if (isDead()) {
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
