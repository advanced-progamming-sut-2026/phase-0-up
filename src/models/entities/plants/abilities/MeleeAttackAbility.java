package models.entities.plants.abilities;

import models.entities.plants.Plant;
import models.entities.plants.abilities.triggers.TriggerStrategy;
import models.entities.projectiles.Element;
import models.game.GameSession;

// Repeating melee strike over a (rowRadius x colRadius) area; damage and reach can grow by stage (Kiwibeast).
public class MeleeAttackAbility extends PlantAbility {
    private int[] damageByStage;
    private int[] rowRadiusByStage;
    private int[] colRadiusByStage;
    private int[] stageUpTicks;
    private Element element;

    private int currentStage;
    private int currentAliveTicks;

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

        super.update(owner, gameSession);
    }

    @Override
    public void execute(Plant owner, GameSession gameSession) {
        AreaAttack.strike(gameSession, owner,
                rowRadiusByStage[currentStage], colRadiusByStage[currentStage],
                damageByStage[currentStage], element);
    }
}
