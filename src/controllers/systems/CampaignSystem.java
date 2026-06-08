package controllers.systems;

import models.game.Level;
import models.user.Profile;

public class CampaignSystem {
    private static CampaignSystem instance;

    private CampaignSystem() {}

    public static synchronized CampaignSystem getInstance() {
        if (instance == null) {
            instance = new CampaignSystem();
        }
        return instance;
    }

    public void completeLevel(Profile profile, String chapterId, int levelIndex){}
    public void unlockNext(Profile profile, Level level){}
    public boolean canEnter(Profile profile, Level level){return false;}


}
