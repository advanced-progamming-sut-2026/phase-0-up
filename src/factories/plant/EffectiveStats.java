package factories.plant;

import models.templates.PlantTemplate.UpgradeSpec;

import java.util.List;

// The plant's stats after folding in every BUFF_* upgrade up to its current level, plus the list of
// SPECIAL_MECHANIC upgrades still to be applied to its freshly-built abilities.
public class EffectiveStats {
    private final int maxHp;
    private final int actionIntervalTicks;
    private final int cost;
    private final double recharge;
    private final int damageBuff;
    private final List<UpgradeSpec> specialMechanics;

    public EffectiveStats(int maxHp, int actionIntervalTicks, int cost, double recharge,
                          int damageBuff, List<UpgradeSpec> specialMechanics) {
        this.maxHp = maxHp;
        this.actionIntervalTicks = actionIntervalTicks;
        this.cost = cost;
        this.recharge = recharge;
        this.damageBuff = damageBuff;
        this.specialMechanics = specialMechanics;
    }

    public int getMaxHp() { return maxHp; }
    public int getActionIntervalTicks() { return actionIntervalTicks; }
    public int getCost() { return cost; }
    public double getRecharge() { return recharge; }
    public int getDamageBuff() { return damageBuff; }
    public List<UpgradeSpec> getSpecialMechanics() { return specialMechanics; }
}
