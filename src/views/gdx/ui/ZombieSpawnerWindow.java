package views.gdx.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.List;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Align;
import models.templates.ZombieTemplate;
import utils.Constants;
import utils.registry.ZombieRegistry;
import views.gdx.core.Assets;

import java.util.ArrayList;
import java.util.Map;
import java.util.function.Predicate;

// The debug zombie spawner: pick a zombie, pick a lane, put it on the board.
//
// The reason this exists beside CheatPanel rather than inside it: the cheat panel is a row of fixed
// strings, and "spawn a zombie" is the one cheat with two arguments worth choosing. Its button has
// always sent `-t normal -l (8, 2)` -- one zombie, one lane -- so looking at any OTHER zombie meant
// restarting the game under -Dpvz.spawn. This is that flag as a control.
//
// Like every other button in the GUI it synthesises the command a player could type and posts it
// through the same sink, so the registry still decides what a zombie is and GameSession still decides
// whether the tile exists. Nothing here can put anything on the lawn that the prompt could not.
public final class ZombieSpawnerWindow extends Window {

    private static final Color FRAME_TEXT = new Color(1f, 0.88f, 0.45f, 1f);
    private static final Color FIELD = new Color(0.06f, 0.07f, 0.10f, 0.98f);
    private static final Color FRAME = new Color(0.11f, 0.13f, 0.17f, 0.94f);
    // The open dropdown sits OVER the lawn rather than over the window, so it is the one surface here
    // that has to be fully opaque.
    private static final Color LIST = new Color(0.05f, 0.06f, 0.09f, 1f);
    private static final float WIDTH = 290f;
    private static final float FIELD_WIDTH = 190f;

    // The rightmost column, which is where a zombie walking in from off-screen would first stand on a
    // tile. ZOMBIE_SPAWN_X (9.5) is off the board and `cheat spawn-zombie` takes a tile, not a
    // continuous x -- so the right EDGE the spec asks for is the last real column.
    private static final int RIGHT_EDGE_COLUMN = Constants.BOARD_COLS - 1;

    private final Predicate<String> sink;
    private final SelectBox<String> zombies;
    private final SelectBox<String> rows;
    private final TextButton spawnButton;

    public ZombieSpawnerWindow(Assets assets, Predicate<String> sink) {
        super("Zombie Spawner  [Z]", windowStyle(assets));
        this.sink = sink;

        setMovable(true);
        setResizable(false);
        // Dragged off the edge it becomes unreachable, and the only way back is to restart the level.
        setKeepWithinStage(true);
        // Room for the title bar above and for the frame's own edge on every other side. Without the
        // side padding the caption column starts ON the border and its first glyph is cut in half.
        pad(38f, 14f, 14f, 14f);
        defaults().pad(4f);

        Skin skin = assets.skin();
        SelectBox.SelectBoxStyle boxStyle = selectBoxStyle(assets);

        zombies = new SelectBox<>(boxStyle);
        zombies.setItems(aliases());
        rows = new SelectBox<>(boxStyle);
        rows.setItems(laneLabels());

        add(caption(skin, "Zombie")).left();
        add(zombies).width(FIELD_WIDTH).row();
        add(caption(skin, "Row")).left();
        add(rows).width(FIELD_WIDTH).row();

        spawnButton = MenuStyles.button(skin, "Spawn", MenuStyles.BUTTON_GREEN);
        spawnButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                spawnSelected();
            }
        });
        add(spawnButton).colspan(2).width(WIDTH - 30f).height(48f).padTop(8f).row();

        pack();
        setWidth(WIDTH);
    }

    // ---- -Dpvz.spawnerCheck ---------------------------------------------------------------------

    public void select(String alias, int row) {
        if (alias != null && !alias.isBlank()) {
            zombies.setSelected(alias);
        }
        rows.setSelected(String.valueOf(row));
    }

    // Fires the button's own ChangeEvent rather than calling spawnSelected -- a check that reached past
    // the listener would pass just as happily with the listener unwired, which is the one thing here
    // worth checking. fire() rather than toggle(): toggle would leave the button stuck looking pressed.
    public void pressSpawn() {
        spawnButton.fire(new ChangeListener.ChangeEvent());
    }

    // Where the zombie dropdown sits, in stage coordinates. Only the harness asks: opening the list
    // by a real click through the Stage is the only way to catch its backing in a screenshot, and
    // calling showList() directly does not survive the frame -- SelectBox closes a list it was not
    // clicked into.
    public com.badlogic.gdx.math.Vector2 zombieBoxCentre() {
        return zombies.localToStageCoordinates(
                new com.badlogic.gdx.math.Vector2(zombies.getWidth() / 2f, zombies.getHeight() / 2f));
    }

    public String selectedAlias() {
        return zombies.getSelected();
    }

    // Straight into the row's zombie list, through GameSession.spawnZombieCheat -- so the wave system
    // is not involved at all: no budget is spent, no wave counter moves, and the spawn queue never
    // hears about it.
    private void spawnSelected() {
        String alias = zombies.getSelected();
        String lane = rows.getSelected();
        if (alias == null || lane == null) {
            return;
        }
        sink.test("cheat spawn-zombie -t " + alias + " -l ("
                + RIGHT_EDGE_COLUMN + ", " + lane + ")");
    }

    // Every alias the registry knows, sorted. Aliases rather than display names because the alias is
    // what the command takes, and a debug tool that shows one name and sends another is a tool that
    // cannot be trusted when it reports nothing happened.
    private static String[] aliases() {
        Map<String, ZombieTemplate> templates =
                ZombieRegistry.getInstance().getZombieTemplatesByAlias();
        if (templates == null || templates.isEmpty()) {
            return new String[] {"ZombieDefault"};
        }
        java.util.List<String> names = new ArrayList<>(templates.keySet());
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names.toArray(new String[0]);
    }

    private static String[] laneLabels() {
        String[] lanes = new String[Constants.BOARD_ROWS];
        for (int row = 0; row < lanes.length; row++) {
            lanes[row] = String.valueOf(row);
        }
        return lanes;
    }

    private static Label caption(Skin skin, String text) {
        Label label = new Label(text, skin);
        label.setAlignment(Align.left);
        label.setColor(FRAME_TEXT);
        return label;
    }

    // ---- styles the skin does not ship ----------------------------------------------------------
    //
    // pvz2_skin declares no WindowStyle and no SelectBoxStyle (it has neither window art nor a
    // dropdown anywhere in the game), which is why ConfirmDialog is a WidgetGroup and Cycler exists
    // instead of a SelectBox. Both are assembled here from parts the skin DOES declare -- its default
    // font, its ListStyle and its ScrollPaneStyle -- rather than being registered into the shared Skin,
    // which Assets owns and every other screen reads.

    // A solid fill rather than UiArt.PANEL. That art is the HUD's 3-slice strip, authored wide and
    // short and mostly translucent; stretched to a panel this tall it washes out to the point where
    // the lawn reads straight through the labels -- and this window sits over the busiest part of the
    // board by definition, since it is used while zombies are on it.
    private static WindowStyle windowStyle(Assets assets) {
        WindowStyle style = new WindowStyle();
        style.titleFont = assets.skin().get(Label.LabelStyle.class).font;
        style.titleFontColor = FRAME_TEXT;
        style.background = assets.solid(FRAME);
        return style;
    }

    private static SelectBox.SelectBoxStyle selectBoxStyle(Assets assets) {
        Skin skin = assets.skin();
        SelectBox.SelectBoxStyle style = new SelectBox.SelectBoxStyle();
        Label.LabelStyle text = skin.get(Label.LabelStyle.class);
        style.font = text.font;
        style.fontColor = new Color(Color.WHITE);
        Drawable field = assets.solid(FIELD);
        style.background = field;
        style.backgroundOver = field;
        style.backgroundOpen = field;
        // The open dropdown gets its own opaque backing. The skin's ScrollPaneStyle and ListStyle both
        // ship with a null background -- they were authored for panels that already sit on art -- so the
        // list of twenty-odd aliases rendered as bare text straight over the lawn, unreadable against
        // sand and impossible to tell from the zombies behind it. COPIED rather than set on the skin's
        // own instances, which Assets owns and the almanac and leaderboard also scroll through.
        ScrollPane.ScrollPaneStyle scroll =
                new ScrollPane.ScrollPaneStyle(skin.get(ScrollPane.ScrollPaneStyle.class));
        scroll.background = assets.solid(LIST);
        List.ListStyle items = new List.ListStyle(skin.get(List.ListStyle.class));
        items.background = assets.solid(LIST);
        style.scrollStyle = scroll;
        style.listStyle = items;
        return style;
    }
}
