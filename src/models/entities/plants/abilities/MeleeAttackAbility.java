package models.entities.plants.abilities;

import models.entities.plants.Plant;
import models.entities.plants.abilities.triggers.TriggerStrategy;
import models.entities.projectiles.Element;
import models.game.GameSession;

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
    }

    @Override
    public void update(Plant owner, GameSession gameSession) {
        currentAliveTicks++;

        if (stageUpTicks != null && currentStage < stageUpTicks.length
                && currentAliveTicks >= stageUpTicks[currentStage]) {
            currentStage++;
        }

        updateFlurry(owner, gameSession);

        super.update(owner, gameSession);
    }

    @Override
    public void execute(Plant owner, GameSession gameSession) {
        AreaAttack.strike(gameSession, owner,
                rowRadiusByStage[currentStage], colRadiusByStage[currentStage],
                damageByStage[currentStage], element);
    }

    @Override
    public void growToMaxStage() {
        this.currentStage = damageByStage.length - 1;
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
