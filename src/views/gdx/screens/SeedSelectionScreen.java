package views.gdx.screens;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import controllers.commands.seedselection.StartLevelCommand;
import models.game.GameSession;
import models.game.SeedPacket;
import models.templates.PlantTemplate;
import models.user.Profile;
import utils.registry.PlantRegistry;
import views.gdx.core.GdxContext;
import views.gdx.ui.MenuStyles;
import views.gdx.ui.PlantIcon;
import views.gdx.ui.SeedCardActor;
import views.gdx.ui.UiArt;

import java.util.ArrayList;
import java.util.List;

// Picking the loadout before a level starts.
//
// Cards are the same SeedCardActor the in-game bank uses, so a plant looks identical here and on the
// lawn. Clicking one posts "add plant -t <name>" or "remove plant -t <name>"; the seed bar's size,
// which plants the level offers and which are locked are all the model's answers, not this screen's.
//
// "Let's Rock" is the one action that does NOT go through the router. The typed "start game" runs
// StartLevelCommand and then GameEngine.startLoop() -- the terminal's blocking loop, which would
// freeze the window. So the Command runs on its own, and moving to IN_GAME lets ScreenManager hand
// over to GameScreen, which drives the same engine from the render loop instead.
public final class SeedSelectionScreen extends MenuScreen {

    // Eight 74-wide cards plus padding is about 640 units, half the design width -- comfortably inside
    // the panel's frame with room for the border art.
    private static final int CARDS_PER_ROW = 8;

    private final UiArt art;
    private Table available;
    private Table chosen;
    private Label slotsLabel;

    public SeedSelectionScreen(GdxContext context) {
        super(context);
        this.art = new UiArt(context.assets());
    }

    @Override
    protected void build(Table root) {
        Table panel = MenuPanel.build(skin, "Choose Your Plants");

        slotsLabel = MenuStyles.label(skin, "", MenuStyles.TEXT);
        panel.add(slotsLabel).padBottom(6f).row();

        chosen = new Table();
        panel.add(chosen).height(SeedCardActor.CARD_HEIGHT + 10f).padBottom(10f).row();

        panel.add(MenuStyles.label(skin, "Available", MenuStyles.TEXT)).padBottom(4f).row();
        available = new Table();
        panel.add(available).padBottom(14f).row();

        Table actions = new Table();
        actions.add(startButton()).width(240f).height(60f).padRight(10f);
        actions.add(backButton()).width(180f).height(50f);
        panel.add(actions).row();

        root.setFillParent(true);
        root.add(panel);

        rebuild();
    }

    // Both rows, from scratch. Adding or removing a seed changes which row a plant belongs to, and
    // rebuilding is cheaper to get right than moving actors between two tables.
    private void rebuild() {
        GameSession session = session();
        available.clear();
        chosen.clear();
        if (session == null) {
            slotsLabel.setText("No level loaded.");
            return;
        }

        List<SeedPacket> selected = session.getSelectedSeeds();
        slotsLabel.setText("Seed bar: " + selected.size() + " / " + session.getMaxSeedSlots());

        for (SeedPacket packet : selected) {
            chosen.add(card(packet.getPlantType(), true)).padRight(6f);
        }
        // Wrapped. A level can offer more plants than fit across 1280 units, and an unwrapped row does
        // not scroll or clip -- it pushes the panel's own frame off both edges of the screen.
        int column = 0;
        for (String plantName : offeredPlants(session)) {
            if (session.isSeedSelected(plantName)) {
                continue;
            }
            available.add(card(plantName, false)).padRight(6f).padBottom(6f);
            if (++column % CARDS_PER_ROW == 0) {
                available.row();
            }
        }
    }

    // A card that adds on click when it is in the available row, and removes when it is on the bar.
    // Right-click boosts, which is the only action with no obvious second place to put it.
    private SeedCardActor card(String plantName, boolean onBar) {
        PlantTemplate template = PlantRegistry.getInstance().getTemplateByName(plantName);
        int cost = template == null ? 0 : template.getCost();
        int recharge = template == null ? 0 : (int) template.getRecharge();

        SeedCardActor actor = new SeedCardActor(context.assets(), art,
                new SeedPacket(plantName, recharge), cost,
                new PlantIcon(context.sprites().get(plantName)));
        actor.setSelected(onBar);
        // The same swell-and-press the menu buttons have. A seed card is the most clicked thing on this
        // screen and was the only one that gave nothing back when the pointer was over it.
        views.gdx.ui.ButtonJuice.applyTo(actor);
        actor.addListener(new ClickListener(-1) {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (getButton() == com.badlogic.gdx.Input.Buttons.RIGHT) {
                    commands.submit("boost plant -t " + plantName);
                } else {
                    commands.submit((onBar ? "remove plant -t " : "add plant -t ") + plantName);
                }
                rebuild();
            }
        });
        return actor;
    }

    // What this level actually offers, matched against what the player owns.
    //
    // The same pairing ConsolePlantMenuRenderer does, and case-insensitively for the same reason: the
    // profile stores plant names lower-cased while the level's pool uses display names.
    private List<String> offeredPlants(GameSession session) {
        Profile profile = session.getPlayer();
        List<String> pool = session.getLevel() == null ? null : session.getLevel().getAvailablePlants();
        if (pool == null || profile == null) {
            return List.of();
        }
        List<String> owned = profile.getUnlockedPlants();
        List<String> result = new ArrayList<>();
        for (String plantName : pool) {
            for (String have : owned) {
                if (have.equalsIgnoreCase(plantName)) {
                    result.add(plantName);
                    break;
                }
            }
        }
        return result;
    }

    private TextButton startButton() {
        TextButton button = MenuStyles.button(skin, "Let's Rock!", MenuStyles.BUTTON_GREEN);
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                start();
            }
        });
        return button;
    }

    private void start() {
        GameSession session = session();
        if (session == null) {
            context.toasts().error("No level loaded.");
            return;
        }
        if (session.getSelectedSeeds().isEmpty()) {
            context.toasts().error("Pick at least one plant -- bare hands won't stop a Gargantuar.");
            return;
        }
        // Sets the menu to IN_GAME and announces the start. GameScreen picks the session up from there.
        new StartLevelCommand(session, context.appSession(),
                context.renderers().plantMenu()).execute();
    }

    private TextButton backButton() {
        TextButton button = MenuStyles.button(skin, "Back", MenuStyles.BUTTON_BROWN);
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                goBack();
            }
        });
        return button;
    }

    private GameSession session() {
        return context.appSession().getCurrentGameSession();
    }

    @Override
    protected void refresh() {
        GameSession session = session();
        if (session != null) {
            // Selection can also change from outside this screen (a boost, a mode that pins a seed),
            // so the count is read live rather than only after a click.
            slotsLabel.setText("Seed bar: " + session.getSelectedSeeds().size()
                    + " / " + session.getMaxSeedSlots());
        }
    }

    // Leaving seed selection abandons the level rather than resuming it, so the session goes with it.
    @Override
    protected void goBack() {
        context.appSession().setCurrentGameSession(null);
        commands.enter(controllers.engine.MenuType.PLAY_MENU.getMenuName());
    }
}
