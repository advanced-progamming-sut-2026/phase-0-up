package models.quests.QuestCondition;

import models.quests.QuestContext;

// Satisfied when a *day* level is won using only mushroom (night) plants (Night or Morning). Both
// halves matter: the challenge is beating a daytime lawn with the night loadout, so winning a sunless
// level with mushrooms is not it. A day level is one whose season has sun falling from the sky --
// every season except Dark Ages, which is the game's night setting.
//
// Mushrooms are identified by name (they all carry "-shroom").
public class OnlyMushroomsCondition implements QuestCondition {
    @Override
    public boolean isSatisfied(QuestContext ctx) {
        return ctx.isWon() && ctx.isDayLevel() && ctx.allPlantedAreMushrooms();
    }
}
