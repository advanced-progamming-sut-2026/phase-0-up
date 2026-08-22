package views.gdx.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import models.user.Profile;

// What the player has to spend, in the two currencies anything outside a level is priced in.
//
// Every screen that offers to sell something needs this and needs it to look identical -- a coin count
// that is gold in the greenhouse and white on the seed screen reads as two different numbers. It is a
// widget rather than a copied helper method because it also has to STAY right: prices are paid from
// screens that never reload, so the balance has to be re-read, and refresh() is the one place that
// happens.
//
// Reads the Profile directly, which is what a view is allowed to do. It only ever reads.
public final class WalletBar extends Table {

    // Both ship in the skin, at the size the shop and the greenhouse already draw them.
    private static final String ICON_COINS = "image_ui_coins_stack_0";
    private static final String ICON_GEMS = "image_ui_gems_stack_1";

    private static final Color COIN_TEXT = new Color(1f, 0.88f, 0.45f, 1f);
    private static final Color GEM_TEXT = new Color(0.55f, 0.82f, 1f, 1f);

    private static final float ICON_SIZE = 30f;
    private static final float COIN_WIDTH = 80f;
    private static final float GEM_WIDTH = 70f;

    private final Label coins;
    private final Label gems;

    public WalletBar(Skin skin) {
        coins = MenuStyles.label(skin, "0", MenuStyles.TEXT);
        coins.setColor(COIN_TEXT);
        gems = MenuStyles.label(skin, "0", MenuStyles.TEXT);
        gems.setColor(GEM_TEXT);

        add(icon(skin, ICON_COINS)).size(ICON_SIZE).padRight(6f);
        add(coins).minWidth(COIN_WIDTH).left().padRight(18f);
        add(icon(skin, ICON_GEMS)).size(ICON_SIZE).padRight(6f);
        add(gems).minWidth(GEM_WIDTH).left();
    }

    // Called every frame by the screens that own one. Two setText calls is cheaper than maintaining a
    // change notification for a number that four different commands can move.
    public void refresh(Profile profile) {
        coins.setText(profile == null ? "0" : String.valueOf(profile.getCoins()));
        gems.setText(profile == null ? "0" : String.valueOf(profile.getGems()));
    }

    // An empty cell rather than a crash if the skin is missing the art: the balance still reads, it
    // just loses its coin.
    private static Actor icon(Skin skin, String id) {
        Drawable art = MenuStyles.drawable(skin, id);
        return art == null ? new Table() : new Image(art);
    }
}
