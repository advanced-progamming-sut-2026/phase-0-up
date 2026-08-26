package views.gdx.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Align;
import models.user.Profile;

// What the player has to spend, wherever they are.
//
// The spec asks for coins and gems to be visible "in all menus, even during gameplay", and this is the
// one widget that answers it -- mounted by MenuScreen for every menu and by GameHud for the lawn, so
// the two can never drift in look or in what they read. It was WalletBar, which three screens each
// built their own copy of; the copies are gone.
//
// Reads the Profile directly, which is what a view is allowed to do, and only ever reads. The one thing
// that WRITES is the debug "+", and it goes out as a Command like every other mutation in this build.
public final class CurrencyHUD extends Table {

    // The game's own coin and gem art, at the size the shop and the greenhouse already draw them.
    private static final String ICON_COINS = "image_ui_coins_stack_0";
    private static final String ICON_GEMS = "image_ui_gems_stack_1";

    private static final Color COIN_TEXT = new Color(1f, 0.88f, 0.45f, 1f);
    private static final Color GEM_TEXT = new Color(0.55f, 0.82f, 1f, 1f);

    private static final float ICON_SIZE = 30f;
    private static final float COIN_WIDTH = 80f;
    private static final float GEM_WIDTH = 70f;

    // How much one press of the debug "+" is worth. A round, generous number: this exists to get past
    // a price while testing, not to be an economy.
    // Bright enough to read as interactive against both a menu panel and the lawn.
    private static final Color PLUS_TINT = new Color(0.62f, 1f, 0.55f, 1f);

    private static final int COIN_STEP = 1000;
    private static final int GEM_STEP = 50;

    private final Label coins;
    private final Label gems;
    // Kept so a flying pickup can be told where to land and what to bounce. The ICON rather than the
    // label: the number changes width as it counts up, so its centre wanders.
    private final Actor coinIcon;
    private final Actor gemIcon;

    public CurrencyHUD(Skin skin) {
        this(skin, null, null);
    }

    // `cheats` is the sink a debug "+" posts through, or null for no debug controls at all. Handed in
    // rather than reached for, because the two mounts reach the model by different routes: a menu
    // constructs CheatAddCommand directly (the router has no edge into the play menu from most menus)
    // and the lawn posts an in-game command string.
    public CurrencyHUD(Skin skin, java.util.function.IntConsumer addCoins,
                       java.util.function.IntConsumer addGems) {
        coins = MenuStyles.label(skin, "0", MenuStyles.TEXT);
        coins.setColor(COIN_TEXT);
        gems = MenuStyles.label(skin, "0", MenuStyles.TEXT);
        gems.setColor(GEM_TEXT);

        coinIcon = icon(skin, ICON_COINS);
        gemIcon = icon(skin, ICON_GEMS);

        boolean debug = addCoins != null && addGems != null;

        add(coinIcon).size(ICON_SIZE).padRight(6f);
        add(coins).minWidth(COIN_WIDTH).left().padRight(debug ? 2f : 18f);
        if (debug) {
            add(plusButton(skin, () -> addCoins.accept(COIN_STEP))).padRight(16f);
        }
        add(gemIcon).size(ICON_SIZE).padRight(6f);
        add(gems).minWidth(GEM_WIDTH).left();
        if (debug) {
            add(plusButton(skin, () -> addGems.accept(GEM_STEP))).padLeft(2f);
        }
    }

    // Called every frame by whatever owns one. Two setText calls is cheaper than maintaining a change
    // notification for a number four different commands can move.
    public void refresh(Profile profile) {
        coins.setText(profile == null ? "0" : String.valueOf(profile.getCoins()));
        gems.setText(profile == null ? "0" : String.valueOf(profile.getGems()));
    }

    public Actor coinIcon() {
        return coinIcon;
    }

    public Actor gemIcon() {
        return gemIcon;
    }

    // The debug "+": small, round, and deliberately quiet.
    //
    // A full TextButton at the skin's own size is the width of the number beside it and turns a readout
    // into a control panel -- which is wrong for something the player is not meant to use. This is a
    // label on the skin's seed-packet plate at a third of its size, with the same hover-and-press
    // feedback every other button in the game has.
    static Table plusButton(Skin skin, Runnable action) {
        Table button = new Table();
        // No background drawable at all.
        //
        // The obvious choice, PANEL_BORDER, is the ornate dialog frame -- and a nine-patch reports a
        // MINIMUM size built from its own corners, which for that one is bigger than the counter it was
        // meant to sit beside. The first attempt drew four buttons the size of seed packets and pushed
        // the whole HUD panel out of shape. A glyph with the game's outlined face and the same hover,
        // press and release every other control has is smaller, reads as a button, and cannot inflate.
        Label plus = MenuStyles.label(skin, "+", MenuStyles.HEADING);
        plus.setAlignment(Align.center);
        plus.setColor(PLUS_TINT);
        button.add(plus).center();
        button.pad(0f, 5f, 2f, 5f);
        ButtonJuice.applyTo(button);
        button.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                action.run();
            }
        });
        return button;
    }

    // An empty cell rather than a crash if the skin is missing the art: the balance still reads, it
    // just loses its coin.
    private static Actor icon(Skin skin, String id) {
        Drawable art = MenuStyles.drawable(skin, id);
        return art == null ? new Table() : new Image(art);
    }
}
