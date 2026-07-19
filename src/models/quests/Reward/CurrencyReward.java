package models.quests.Reward;

import models.user.Profile;

// Currency reward: adds coins or gems to the player's wallet.
public class CurrencyReward extends Reward {
    public enum Currency { COINS, GEMS }

    private final Currency currency;
    private final int amount;

    public CurrencyReward(Currency currency, int amount) {
        this.currency = currency;
        this.amount = amount;
    }

    @Override
    public void grant(Profile profile) {
        if (profile == null || amount <= 0) {
            return;
        }
        if (currency == Currency.COINS) {
            profile.addCoins(amount);
        } else {
            profile.addGems(amount);
        }
    }

    @Override
    public String describe() {
        return amount + " " + (currency == Currency.COINS ? "coins" : "gems");
    }

    public Currency getCurrency() { return currency; }
    public int getAmount() { return amount; }
}
