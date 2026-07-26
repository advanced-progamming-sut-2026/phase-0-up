package models.entities.zombies.Components;

import models.entities.plants.Plant;
import models.entities.zombies.Abilities.RollTheBarrel;
import models.entities.zombies.Abilities.ZombieAbility;
import models.entities.zombies.Zombie;
import models.entities.projectiles.Element;
import models.entities.projectiles.Trajectory;
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
    // The total HP this zombie was born with (body + every armor layer). Recorded once, because layers
    // are POPPED as they are destroyed -- so summing the surviving layers can never recover it. Abilities
    // that trigger at a fraction of health (the Gargantuar's imp throw at 50%) measure against this.
    private final int maxTotalHp;

    private int poisonDps;
    private int poisonDurationTicks;
    private int poisonTickTimer;

    // The last plant to deal damage to this zombie -- i.e. who gets credit for the kill. Environmental
    // damage (lawn mower, radioactive sun, poison ticks) passes a null attacker and does not overwrite
    // it, so a plant that softened the zombie up still keeps the attribution.
    private Plant lastAttacker;

    // Total HP immediately before the most recent damage application. Lets a scorer ask, after the
    // zombie is already dead, whether the killing blow found it untouched -- which is exactly what a
    // "killed it outright, from full health" bonus needs and cannot reconstruct from a corpse at 0 HP.
    private int hpBeforeLastHit;

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
        this.maxTotalHp = getTotalHP();
    }

    // The HP this zombie started with, for fraction-of-health triggers. Never zero, so callers can
    // divide by it safely.
    public int getMaxTotalHp() {
        return maxTotalHp > 0 ? maxTotalHp : 1;
    }

    // Whether the most recent blow struck this zombie at full health -- i.e. it was felled outright
    // rather than worn down. Only meaningful once it is dead.
    public boolean wasKilledFromFullHealth() {
        return hpBeforeLastHit >= getMaxTotalHp();
    }

    public boolean isDead() {
        return layers.isEmpty() || getTotalHP() <= 0;
    }

    // The plant credited with this zombie's death (the last one to damage it), or null if only
    // environmental damage ever hit it.
    public Plant getLastAttacker() {
        return lastAttacker;
    }

    public void addLayer(HealthLayer layer) {
        layers.push(layer);
    }

    // Straight-line damage, the common case: every caller that has no trajectory to speak of (melee
    // bites, explosions, mowers, poison ticks) lands as DIRECT.
    public void applyDamage(int damage, Element element, Plant attacker) {
        applyDamage(damage, element, attacker, Trajectory.DIRECT);
    }

    // How the damage arrived matters as much as what it is made of: element decides whether a blow
    // seeps past armor, trajectory decides whether it goes over the front of it. A LOBBED shot arcs
    // above anything the zombie is carrying in front (a newspaper, a shoved barrel) and strikes what
    // is behind, which is exactly why a Melon-pult is the answer to a shield a Peashooter cannot pass.
    public void applyDamage(int damage, Element element, Plant attacker, Trajectory trajectory) {
        if (isDead()) return;

        hpBeforeLastHit = getTotalHP();   // snapshot before the blow lands, for one-shot scoring

        if (attacker != null) {
            lastAttacker = attacker;   // remember who to credit for the eventual kill
        }

        if (element != null && element.piercesBaseArmor()) {
            applyToBaseBody(damage);
        } else if (trajectory == Trajectory.LOBBED) {
            peelFromTopOverShields(damage);
        } else {
            peelFromTop(damage);
        }

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

    // A lobbed shot passes over anything carried in front and hits the first layer that is actually
    // worn. Only the shields on TOP of the stack are skipped -- once a worn layer is reached the shot
    // peels normally from there down, so a newspaper sitting under a cone still gets no protection
    // from being nominally "below" it.
    //
    // The skipped shields are lifted off, the damage is applied to what remains, and they are put back
    // in the order they came off: flying over a shield must not damage it, and must not disturb the
    // stack a later straight shot will meet.
    private void peelFromTopOverShields(int damage) {
        Stack<HealthLayer> lifted = new Stack<>();
        while (!layers.isEmpty() && layers.peek().getType() != null
                && layers.peek().getType().isFrontShield()) {
            lifted.push(layers.pop());
        }

        if (layers.isEmpty()) {
            // Nothing but shields (a stray barrel with no body) -- there is nothing behind to hit, so
            // put them back untouched rather than letting the shot fall through to nowhere.
            restore(lifted);
            return;
        }

        peelFromTop(damage);

        // Only put the shields back if something survived behind them. If the blow emptied the stack
        // the zombie is dead, and restoring its newspaper would leave isDead() reading false -- a
        // corpse held upright by the very shield the shot flew over.
        if (!layers.isEmpty()) {
            restore(lifted);
        }
    }

    private void restore(Stack<HealthLayer> lifted) {
        while (!lifted.isEmpty()) {
            layers.push(lifted.pop());
        }
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
    // Poison-over-time: ticked down here, dealing poisonDps once per second for its duration.
}
