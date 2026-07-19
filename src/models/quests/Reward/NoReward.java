package models.quests.Reward;

import models.user.Profile;

// Null-object reward for a quest whose reward spec is missing or unrecognised: grants nothing rather
// than forcing every caller to null-check.
public class NoReward extends Reward {
    @Override
    public void grant(Profile profile) { }

    @Override
    public String describe() {
        return "nothing";
    }
}
