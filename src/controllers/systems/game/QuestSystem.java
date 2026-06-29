package controllers.systems.game;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import models.quests.Quest;
import models.quests.QuestContext;
import models.quests.Reward.Reward;
import models.user.Profile;

import java.util.List;

public class QuestSystem {

    private QuestContext currentLevelContext;

    public void startTrackingLevel(GameSession session){};
    public void recordSunCollected(int amount){}
    public void recordZombieKilled(Zombie zombie, Plant killer){}
    public void recordPlantLost(){}
    public void evaluateActiveQuests(Profile profile, GameSession session){}
    public List<Quest> getSortedQuestsForLog(Profile profile){return null;}
    public void grant(Reward reward){}
}
