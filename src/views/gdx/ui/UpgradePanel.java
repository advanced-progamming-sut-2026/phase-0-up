package views.gdx.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import models.game.GameSession;
import models.game.gamemodes.BeghouledMode;
import models.minigames.Upgrade;
import views.gdx.core.Assets;

import java.util.ArrayList;
import java.util.List;

// Beghouled's shop: spend the sun matches paid out to promote every plant of one type at once.
//
// The mode has had `upgrade(session, fromType)` and a full `getUpgrades()` table since it was written,
// and `upgrade -t <type>` has always been typeable. In the GUI there was no way to reach any of it --
// which made the sun a match pays out do nothing at all, and left the mode's whole economy invisible.
//
// Reads the model and writes nothing: a click synthesises the same command string the prompt takes, so
// the cost check, the "not one of those on the lawn" refusal and the promotion itself all stay in
// BeghouledMode. The panel only decides what to OFFER.
public final class UpgradePanel extends Table {

    // Greyed rather than hidden when the player cannot afford it. Hiding a row would make the list jump
    // about as sun comes and goes, and the whole point of the panel is to show what to save up for.
    private static final Color AFFORDABLE = Color.WHITE;
    private static final Color TOO_DEAR = new Color(0.55f, 0.55f, 0.58f, 1f);

    // A row the board has none of. Distinct from "cannot afford": the answer is not "make more sun", it
    // is "there is nothing to promote", and the model refuses it with that message.
    private static final Color NOT_ON_BOARD = new Color(0.45f, 0.40f, 0.35f, 1f);

    private final GameSession session;

    // One row per upgrade, kept so refresh can recolour them without rebuilding the table -- this runs
    // every frame, and Scene2D relayout is not free.
    private final List<Row> rows = new ArrayList<>();

    private record Row(Upgrade upgrade, Label name, Label cost) { }

    public UpgradePanel(Assets assets, UiArt art, GameSession session,
                        java.util.function.Predicate<String> onUpgrade) {
        this.session = session;

        setBackground(art.stretchable(UiArt.PANEL, 0.28f));
        pad(8f);

        Label title = new Label("Upgrades", assets.skin());
        title.setAlignment(Align.center);
        add(title).colspan(2).padBottom(6f).row();

        BeghouledMode mode = modeOf(session);
        if (mode == null) {
            return;
        }
        for (Upgrade upgrade : mode.getUpgrades().values()) {
            addRow(assets, art, upgrade, onUpgrade);
        }
    }

    private void addRow(Assets assets, UiArt art, Upgrade upgrade,
                        java.util.function.Predicate<String> onUpgrade) {
        Table button = new Table();
        button.setBackground(art.stretchable(UiArt.SEED_PACKET, 0.22f));
        button.pad(4f, 8f, 4f, 8f);

        // "Peashooter -> Repeater", because the from-name alone does not say what you are buying and
        // the to-name alone does not say what it costs you.
        Label name = new Label(upgrade.getFromPlant() + " -> " + upgrade.getToPlant(), assets.skin());
        Label cost = new Label(String.valueOf(upgrade.getCost()), assets.skin());
        cost.setAlignment(Align.right);

        button.add(name).left().expandX();
        button.add(cost).right().padLeft(12f);
        button.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                // Fired whether or not it is affordable. The refusal is the model's to give and it
                // arrives as a toast that says WHY -- a dead button just looks broken.
                onUpgrade.test(upgrade.getFromPlant());
            }
        });
        add(button).width(260f).padBottom(4f).colspan(2).row();
        rows.add(new Row(upgrade, name, cost));
    }

    // Recoloured every frame from the live board. Cheap: it is a handful of label colours and an
    // integer compare, and there is no change notification for sun to hang it off instead.
    public void refresh() {
        BeghouledMode mode = modeOf(session);
        if (mode == null) {
            return;
        }
        int sun = session.getSunAmount();
        for (Row row : rows) {
            Color colour;
            if (countOnBoard(mode, row.upgrade().getFromPlant()) == 0) {
                colour = NOT_ON_BOARD;
            } else {
                colour = sun >= row.upgrade().getCost() ? AFFORDABLE : TOO_DEAR;
            }
            row.name().setColor(colour);
            row.cost().setColor(colour);
        }
    }

    // Counted from the mode's own board rather than from the map's Cells. The two agree -- syncMap keeps
    // them in step -- but the board is what `upgrade` actually promotes, so it is the honest source for
    // "is there anything here to promote".
    private static int countOnBoard(BeghouledMode mode, String plantType) {
        String[][] board = mode.board();
        if (board == null) {
            return 0;
        }
        int found = 0;
        for (String[] line : board) {
            for (String type : line) {
                if (type != null && type.equalsIgnoreCase(plantType)) {
                    found++;
                }
            }
        }
        return found;
    }

    public static BeghouledMode modeOf(GameSession session) {
        return session != null && session.getMode() instanceof BeghouledMode mode ? mode : null;
    }
}
