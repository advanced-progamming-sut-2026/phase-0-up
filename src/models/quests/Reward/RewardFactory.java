package models.quests.Reward;

import models.templates.QuestTemplate.RewardSpec;

// Builds the right Reward strategy from an authored reward spec. This is the single place that maps
// the data's reward category onto a concrete reward class, so nothing else has to switch on the type.
public final class RewardFactory {
    private RewardFactory() { }

    public static Reward create(RewardSpec spec) {
        if (spec == null || spec.getCategory() == null) {
            return new NoReward();
        }
        switch (spec.getCategory().toUpperCase()) {
            case "CURRENCY":
                return new CurrencyReward(parseCurrency(spec.getCurrency()), spec.getAmount());
            case "INVENTORY":
                return new InventoryReward(spec.getItem(), spec.getAmount());
            case "UNLOCKABLE":
                return new UnlockableReward(parseTarget(spec.getTarget()), spec.getItem());
            default:
                return new NoReward();
        }
    }

    private static CurrencyReward.Currency parseCurrency(String raw) {
        return "GEMS".equalsIgnoreCase(raw) ? CurrencyReward.Currency.GEMS : CurrencyReward.Currency.COINS;
    }

    private static UnlockableReward.Target parseTarget(String raw) {
        return "LEVEL".equalsIgnoreCase(raw) ? UnlockableReward.Target.LEVEL : UnlockableReward.Target.PLANT;
    }
}
