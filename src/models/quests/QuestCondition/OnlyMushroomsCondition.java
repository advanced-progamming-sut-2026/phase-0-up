package models.quests.QuestCondition;

import models.quests.QuestContext;

// Satisfied when the level is won using only mushroom (night) plants (Night or Morning). Mushrooms
// are identified by name (they all end in "-shroom").
public class OnlyMushroomsCondition implements QuestCondition {
    @Override
    public boolean isSatisfied(QuestContext ctx) {
        return ctx.isWon() && ctx.allPlantedAreMushrooms();
    }
}
