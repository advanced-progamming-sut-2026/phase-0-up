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

// A zombie's layered health: a BASE_BODY layer at the bottom (HP from the zombie's own Hitpoints)
// with armor layers stacked on top. Damage peels from the top layer down; poison bypasses armor
// straight to the body.
public class HealthComponent {
    private static final int TICKS_PER_SECOND = 10;

    private Stack<HealthLayer> layers;
    private List<ArmorType> armorTypes;
    private Zombie currentZombie;

    private int poisonDps;
    private int poisonDurationTicks;
    private int poisonTickTimer;

    public HealthComponent(int baseHp, List<ArmorType> armorTypes, Zombie currentZombie) {
        layers = new Stack<>();
        this.armorTypes = armorTypes;
        this.currentZombie = currentZombie;

        // base body first (bottom of the stack), then each armor on top in listed order
        addLayer(new HealthLayer(ArmorType.BASE_BODY, baseHp));
        if (armorTypes != null) {
            for (ArmorType a : armorTypes) {
                addLayer(new HealthLayer(a));
            }
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

        if (element != null && element.piercesBaseArmor()) {
            applyToBaseBody(damage);
        } else {
            peelFromTop(damage);
        }

        //TODO: lobbed (overhead) delivery should ignore front shields; pass Trajectory here when needed

        if (isDead()) {
            die();
        }
    }

    // Poison and similar seep past every armor layer and damage the body directly.
    private void applyToBaseBody(int damage) {
        for (HealthLayer layer : layers) {
            if (layer.getType() == ArmorType.BASE_BODY) {
                layer.takeDamage(damage);
                break;
            }
        }
        layers.removeIf(layer -> layer.getCurrentHp() <= 0);
    }

    // Normal damage is absorbed by the outermost layer; a depleted layer is popped immediately so
    // the remaining damage carries through to the next one (never spins on a 0-HP layer).
    private void peelFromTop(int damage) {
        int remainingDamage = damage;
        while (remainingDamage > 0 && !layers.isEmpty()) {
            HealthLayer topLayer = layers.peek();
            int absorbed = topLayer.takeDamage(remainingDamage);
            remainingDamage -= absorbed;

            if (topLayer.getCurrentHp() <= 0) {
                layers.pop();
            }
        }
    }

    public boolean tryRemoveMetallicArmor() {
        if (layers.isEmpty()) {
            return false;
        }
        ArmorType top = layers.peek().getType();
        if (top == ArmorType.BUCKET || top == ArmorType.SHOULDER_ARMOR || top == ArmorType.CROWN) {
            layers.pop();
            return true;
        }
        return false;
    }

    private void die() {
        currentZombie.getState().setAction(ActionState.DYING);

        if (!layers.isEmpty() && layers.peek().getType() == ArmorType.BARREL) {
            for (ZombieAbility a : currentZombie.getAbilities()) {
                if (a instanceof RollTheBarrel) {
                    ((RollTheBarrel) a).onRollerDeath(currentZombie);
                }
            }
        }

        // a wizard-cursed plant returns to normal when its curser dies
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

    // Goo Peashooter: applies a damage-over-time that seeps past armor. Strongest poison wins and the
    // duration refreshes. Ticked once per game-second from the owning zombie's update loop.
    public void applyPoison(int dps, int durationTicks) {
        this.poisonDps = Math.max(this.poisonDps, dps);
        this.poisonDurationTicks = Math.max(this.poisonDurationTicks, durationTicks);
        this.poisonTickTimer = 0;
    }

    // Called every tick by Zombie.update(): advances the poison damage-over-time.
    public void update() {
        if (isDead() || poisonDurationTicks <= 0) {
            return;
        }
        poisonDurationTicks--;
        poisonTickTimer++;
        if (poisonTickTimer >= TICKS_PER_SECOND) {
            poisonTickTimer = 0;
            applyDamage(poisonDps, Element.POISON, null); // POISON bypasses armor, straight to the body
        }
    }

    // Scales every layer's HP (Hypno-shroom buff on a hypnotized ally).
    public void scaleHp(double multiplier) {
        for (HealthLayer layer : layers) {
            layer.scale(multiplier);
        }
    }

    public int getTotalHP() {
        int totalHP = 0;
        for (HealthLayer h : this.layers) {
            totalHP += h.getCurrentHp();
        }
        return totalHP;
    }

    public boolean hasArmor() {
        return layers.size() > 1;
    }

    public Stack<HealthLayer> getLayers() {
        return layers;
    }
    //TODO: apply poison overtime damage
}
