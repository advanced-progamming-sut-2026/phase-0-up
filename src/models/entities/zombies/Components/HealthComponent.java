package models.entities.zombies.Components;

import models.entities.plants.Plant;
import models.entities.zombies.Abilities.RollTheBarrel;
import models.entities.zombies.Abilities.ZombieAbility;
import models.entities.zombies.Zombie;
import models.entities.projectiles.Element;
import models.map.Cell;
import models.map.Row;

import java.util.List;
import java.util.Stack;

public class HealthComponent {
    private Stack<HealthLayer> layers;
    private List<ArmorType> armorTypes;
    private Zombie currentZombie;

    public HealthComponent(List<ArmorType> armorTypes , Zombie currentZombie) {
        layers = new Stack<>();
        this.armorTypes = armorTypes;
        for(ArmorType a : armorTypes){
            addLayer(new HealthLayer(a));
        }
        this.currentZombie = currentZombie;
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

        if (element!= null && element.piercesBaseArmor()) {
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
        if(!layers.isEmpty() && layers.peek().getType() == ArmorType.BARREL){
            for(ZombieAbility a :currentZombie.getAbilities()){
                if(a instanceof RollTheBarrel){
                    ((RollTheBarrel) a).onRollerDeath(currentZombie);
                }
            }
        }

        for (Row row : currentZombie.getGameSession().getMap().getRows()) {
            for (Cell cell : row.getCells()) {
                if (cell.hasPlant()) {
                    Plant p = cell.getCurrentPlant();
                    if (p != null && p.isCat() && p.getCursedByWizard() == currentZombie) {
                        p.revertFromCat();
                    }
                }
            }
        }



    }

    public int getTotalHP(){
        int totalHP = 0;
        for(HealthLayer h : this.layers){
            totalHP += h.getCurrentHp();
        }
        return totalHP;
    }

    public boolean hasArmor() {
        if(layers.size() == 1) return false;
        else return true;
    }

    public Stack<HealthLayer> getLayers() {
        return layers;
    }
//TODO: apply poison overtime damage
}
