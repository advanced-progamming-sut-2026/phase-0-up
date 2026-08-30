package models.entities.plants.abilities;

import models.entities.plants.Plant;
import models.entities.plants.abilities.triggers.TriggerStrategy;
import models.entities.projectiles.Element;
import models.game.GameSession;

import java.util.Arrays;

// Repeating melee strike over a (rowRadius x colRadius) area; damage and reach can grow by stage (Kiwibeast).
public class MeleeAttackAbility extends PlantAbility implements Growable {
    private int[] damageByStage;
    private int[] rowRadiusByStage;
    private int[] colRadiusByStage;
    private int[] stageUpTicks;
    private Element element;

    private int currentStage;
    private int currentAliveTicks;

    // plant food flurry (Bonk Choy, Wasabi Whip)
    private int flurryTicksRemaining;
    private int flurryStrikeTimer;
    private static final int FLURRY_STRIKE_INTERVAL = 2;

    public MeleeAttackAbility(int actionInterval, TriggerStrategy triggerStrategy, int[] damageByStage,
                              int[] rowRadiusByStage, int[] colRadiusByStage, int[] stageUpTicks, Element element) {
        super(actionInterval, triggerStrategy);
        this.damageByStage = damageByStage;
        this.rowRadiusByStage = rowRadiusByStage;
        this.colRadiusByStage = colRadiusByStage;
        this.stageUpTicks = stageUpTicks;
        this.element = element;
        this.currentStage = 0;
        this.currentAliveTicks = 0;

        // Ready the moment it is planted. PlantAbility starts every ability on a full cooldown, which
        // for a shooter is right -- it is a firing RATE -- but a melee plant's interval is how long it
        // spends recovering AFTER a strike. Starting on it meant a Chomper, whose interval is forty
        // seconds of chewing, stood there doing nothing for forty seconds after being planted while a
        // zombie ate it. Phat Beet was two seconds late and Bonk Choy a quarter of one.
        this.cooldownTimer = 0;
    }

    // Ticks spent visibly winding up before the strike lands. Same trade as ShootProjectileAbility's:
    // the ability still fires once per actionInterval, only the moment within the cycle shifts, and
    // what it buys is a window for the view to play the swing in.
    //
    // Without it these plants had no animation at all. The renderer starts an action clip on the rising
    // edge of isWindingUp(), and MeleeAttackAbility never reported one -- so Bonk Choy's five punch
    // clips and Chomper's bite sat in the dump unused while the plants stood still and zombies
    // silently lost health.
    private static final int WIND_UP_TICKS = 3;
    private int windUpRemaining = -1;

    @Override
    public boolean isWindingUp() {
        return windUpRemaining >= 0;
    }

    // Still busy with the last one. For most melee plants this is a fraction of a second and nothing
    // reads it; for a Chomper it is forty seconds of chewing, which is a whole state of its own and
    // the art ships a loop for it (`special_idle`). Only true once it has ACTUALLY bitten -- a freshly
    // planted Chomper is hungry, not chewing.
    public boolean isRecovering() {
        return hasStruck && windUpRemaining < 0 && cooldownTimer > 0;
    }

    private boolean hasStruck;

    @Override
    public void update(Plant owner, GameSession gameSession) {
        currentAliveTicks++;

        if (stageUpTicks != null && currentStage < stageUpTicks.length
                && currentAliveTicks >= stageUpTicks[currentStage]) {
            currentStage++;
        }

        // The swing started by execute() runs down here, and the damage lands on the tick it ends.
        if (windUpRemaining > 0) {
            windUpRemaining--;
        } else if (windUpRemaining == 0) {
            windUpRemaining = -1;
            strike(owner, gameSession);
        }

        updateFlurry(owner, gameSession);

        super.update(owner, gameSession);
    }

    @Override
    public boolean canExecute(Plant owner, GameSession gameSession) {
        if (windUpRemaining >= 0) {
            return false;   // already mid-swing
        }
        return super.canExecute(owner, gameSession);
    }

    // Begins the swing. The damage itself lands in strike(), once the wind-up elapses.
    @Override
    public void execute(Plant owner, GameSession gameSession) {
        windUpRemaining = WIND_UP_TICKS;
    }

    private void strike(Plant owner, GameSession gameSession) {
        hasStruck = true;
        AreaAttack.strike(gameSession, owner,
                rowRadiusByStage[currentStage], colRadiusByStage[currentStage],
                damageByStage[currentStage], element);
    }

    @Override
    public void growToMaxStage() {
        this.currentStage = damageByStage.length - 1;
    }

    @Override
    public int growthStage() {
        return currentStage;
    }

    // Upgrade (GROWTH_STAGE_MAX_UP): appends one more growth stage (Kiwibeast "Max Size +1"),
    // extrapolating damage/reach from the current top stage.
    public void addGrowthStage() {
        int n = damageByStage.length;
        if (n == 0) {
            return;
        }
        int lastDamage = damageByStage[n - 1];
        int damageStep = n >= 2 ? lastDamage - damageByStage[n - 2] : lastDamage;
        damageByStage = push(damageByStage, lastDamage + damageStep);
        rowRadiusByStage = push(rowRadiusByStage, rowRadiusByStage[rowRadiusByStage.length - 1]);
        colRadiusByStage = push(colRadiusByStage, colRadiusByStage[colRadiusByStage.length - 1] + 1);
        int lastTick = stageUpTicks.length > 0 ? stageUpTicks[stageUpTicks.length - 1] : 240;
        stageUpTicks = push(stageUpTicks, lastTick + 480);
    }

    private static int[] push(int[] arr, int value) {
        int[] result = Arrays.copyOf(arr, arr.length + 1);
        result[arr.length] = value;
        return result;
    }

    // Plant food: one powerful boosted strike over a slightly wider reach (Phat Beet, Kiwibeast).
    public void plantFoodStrike(Plant owner, GameSession gameSession, int damageMultiplier) {
        int strikeDamage = damageByStage[currentStage] * damageMultiplier;
        int rowR = rowRadiusByStage[currentStage] + 1;
        int colR = colRadiusByStage[currentStage] + 1;
        AreaAttack.strike(gameSession, owner, rowR, colR, strikeDamage, element);
    }

    // Plant food: a rapid flurry of area strikes over a duration (Bonk Choy, Wasabi Whip).
    public void activatePlantFoodFlurry(int durationTicks) {
        this.flurryTicksRemaining = durationTicks;
        this.flurryStrikeTimer = 0;
    }

    @Override
    public boolean isPlantFoodBusy() {
        return flurryTicksRemaining > 0;
    }

    private void updateFlurry(Plant owner, GameSession gameSession) {
        if (flurryTicksRemaining <= 0) return;
        flurryTicksRemaining--;

        if (flurryStrikeTimer > 0) {
            flurryStrikeTimer--;
            return;
        }

        int rowR = rowRadiusByStage[currentStage] + 1;
        int colR = colRadiusByStage[currentStage] + 1;
        AreaAttack.strike(gameSession, owner, rowR, colR, damageByStage[currentStage], element);
        flurryStrikeTimer = FLURRY_STRIKE_INTERVAL;
    }
}
