package models.quests.Reward;

import models.user.Profile;

// A quest reward, applied polymorphically to the player's profile. Concrete strategies (Currency,
// Inventory, Unlockable) each know how to grant themselves; callers just invoke grant().
public abstract class Reward {
    // Applies this reward to the profile (adds currency, unlocks something, or stocks the inventory).
    public abstract void grant(Profile profile);

    // A short human-readable label for the travel-log listing (e.g. "200 gems", "10 seed packets").
    public abstract String describe();
}
