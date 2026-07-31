package models.quests.QuestCondition;

import models.quests.QuestContext;

// Satisfied when a *day* level is won on mushrooms alone (Night or Morning); they carry "-shroom".
public class OnlyMushroomsCondition implements QuestCondition {
    @Override
    public boolean isSatisfied(QuestContext ctx) {
        return ctx.isWon() && ctx.isDayLevel() && ctx.allPlantedAreMushrooms();
    }
}
