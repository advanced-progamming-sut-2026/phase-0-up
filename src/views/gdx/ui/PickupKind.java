package views.gdx.ui;

// The four things a dying zombie can hand the player, from the view's side.
//
// Not `models.entities.collectibles.Collectibles`, and deliberately so: that enum is the model's three
// LOOT drops and knows nothing about plant food, which reaches the player by a different route entirely
// (`GameSession.increasePlantFoodCount`, off the glow flag) and is not a Collectible at all. What the
// view needs is one list of "things that fly to a counter", which is a view concern -- it exists because
// four different rewards share one animation, not because the model groups them.
public enum PickupKind {

    PLANT_FOOD("image_ui_hud_ingame_plantfood_bank_filled_slot", "+1 Plant Food"),
    COIN("image_ui_coins_stack_0", "+50 Coins"),
    GEM("image_ui_gems_stack_1", "+1 Gem"),
    // A pot is not a currency and has no counter of its own on the lawn, so it borrows the coin's --
    // which is also where it ends up when the greenhouse is full and CombatSystem pays out a coin
    // instead. The label is what tells the player which of the two actually happened.
    POT("image_ui_coins_stack_0", "+1 Greenhouse Pot");

    private final String iconId;
    private final String label;

    PickupKind(String iconId, String label) {
        this.iconId = iconId;
        this.label = label;
    }

    public String iconId() {
        return iconId;
    }

    public String label() {
        return label;
    }
}
