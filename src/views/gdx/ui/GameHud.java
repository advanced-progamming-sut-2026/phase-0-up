package views.gdx.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.FitViewport;
import models.game.GameSession;
import models.game.SeedPacket;
import views.gdx.core.Assets;
import views.gdx.core.DebugFlags;
import views.gdx.core.PvZGame;
import views.gdx.input.ToolState;
import views.gdx.sprite.SpriteRegistry;

import java.util.ArrayList;
import java.util.List;

// Everything drawn over the lawn: the wallet, the seed bank, the tools, the wave bar and the pause
// overlay.
//
// Its own Stage on a 1280x720 FitViewport, deliberately NOT the lawn's camera. The board is drawn in
// background-pixel space and scrolls to keep the lawn centred; anchoring HUD widgets to that would drag
// them around with it. A separate virtual resolution is also what lets the HUD stay a fixed size while
// the board letterboxes.
//
// Reads the model and never writes it. Selecting a card sets ToolState, which is view state; the only
// thing that ever changes the game is a synthesised command, and that happens in CommandBridge.
public final class GameHud implements Disposable {

    private static final Color PANEL = new Color(0.10f, 0.12f, 0.16f, 0.80f);
    private static final Color TOOL_ARMED = new Color(1f, 0.86f, 0.35f, 0.95f);
    private static final Color WAVE_TRACK = new Color(0.10f, 0.12f, 0.16f, 0.75f);
    private static final Color WAVE_FILL = new Color(0.85f, 0.25f, 0.22f, 0.95f);
    private static final Color DIM = new Color(0f, 0f, 0f, 0.6f);

    private final Assets assets;
    private final UiArt art;
    private final GameSession session;
    private final ToolState tools;
    private final Stage stage;

    private final Label sunLabel;
    private final Label plantFoodLabel;
    private final Label waveLabel;
    private final Table cardRow;
    private final Table shovelButton;
    private final Table plantFoodButton;
    private final Table pauseOverlay;
    private final WaveBar waveBar;

    private final List<SeedCardActor> cards = new ArrayList<>();

    public GameHud(Assets assets, SpriteRegistry sprites, GameSession session, ToolState tools) {
        this.assets = assets;
        this.art = new UiArt(assets);
        this.session = session;
        this.tools = tools;
        this.stage = new Stage(new FitViewport(PvZGame.VIRTUAL_WIDTH, PvZGame.VIRTUAL_HEIGHT));

        this.sunLabel = new Label("0", assets.skin());
        this.plantFoodLabel = new Label("0", assets.skin());
        this.waveLabel = new Label("", assets.skin());
        this.cardRow = new Table();
        this.waveBar = new WaveBar(assets, this.art);

        buildSeedBank(sprites);
        this.shovelButton = toolButton(UiArt.SHOVEL_ICON, "Shovel [S]", ToolState.Tool.SHOVEL);
        this.plantFoodButton = toolButton(UiArt.PLANTFOOD_BUTTON, "Food [F]",
                ToolState.Tool.PLANT_FOOD);

        layoutRoot();
        this.pauseOverlay = buildPauseOverlay();
        stage.addActor(pauseOverlay);
        pauseOverlay.setVisible(false);

        if (DebugFlags.UI_DEBUG) {
            stage.setDebugAll(true);
        }
    }

    public Stage stage() {
        return stage;
    }

    // The cheat panel is created on demand and starts hidden: it is a testing tool, and having it on
    // screen by default would cover the lawn during normal play.
    private CheatPanel cheats;

    public void installCheats(java.util.function.Predicate<String> sink) {
        if (cheats != null) {
            return;
        }
        cheats = new CheatPanel(assets, art, sink);
        Table holder = new Table();
        holder.setFillParent(true);
        // Middle-right, clear of the seed bank on the left and the toasts in the top corner.
        holder.right().padRight(12f);
        holder.add(cheats);
        holder.setVisible(false);
        stage.addActor(holder);
        cheatHolder = holder;
    }

    private Table cheatHolder;

    public void toggleCheats() {
        if (cheatHolder != null) {
            cheatHolder.setVisible(!cheatHolder.isVisible());
        }
    }

    // One card per packet the player actually brought. Read once: the bank does not change mid-level
    // (modes that adjust the slots do so in onStart, before the first frame).
    private void buildSeedBank(SpriteRegistry sprites) {
        List<SeedPacket> packets = session.getSelectedSeeds();
        if (packets == null) {
            return;
        }
        for (SeedPacket packet : packets) {
            SeedCardActor card = new SeedCardActor(assets, art, packet, costOf(packet.getPlantType()),
                    new PlantIcon(sprites.get(packet.getPlantType())));
            card.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    tools.selectSeed(card.plantType());
                }
            });
            cards.add(card);
            cardRow.add(card).size(SeedCardActor.CARD_WIDTH, SeedCardActor.CARD_HEIGHT).padRight(6f);
        }
    }

    // The template is the one authority on price, so the card and the plant command cannot disagree
    // about what something costs.
    private static int costOf(String plantType) {
        try {
            models.templates.PlantTemplate template =
                    utils.registry.PlantRegistry.getInstance().getTemplateByName(plantType);
            return template == null ? 0 : template.getCost();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    // A square button carrying the shipped icon for its tool, falling back to a labelled panel if the
    // region is ever missing so the control never disappears entirely.
    private Table toolButton(String iconId, String fallbackText, ToolState.Tool tool) {
        Table button = new Table();
        button.setBackground(art.stretchable(UiArt.PANEL, 0.28f));
        com.badlogic.gdx.scenes.scene2d.utils.Drawable icon = art.drawable(iconId);
        if (icon != null) {
            button.add(new com.badlogic.gdx.scenes.scene2d.ui.Image(icon)).size(52f, 40f).pad(4f);
        } else {
            button.pad(8f, 12f, 8f, 12f);
            button.add(new Label(fallbackText, assets.skin()));
        }
        button.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                tools.selectTool(tool);
            }
        });
        return button;
    }

    private void layoutRoot() {
        Table root = new Table();
        root.setFillParent(true);
        root.top().left().pad(10f);

        // Sun bank: the game's own sun icon with the count beside it, on the HUD's own panel art.
        Table counters = new Table();
        counters.setBackground(art.stretchable(UiArt.PANEL, 0.28f));
        counters.pad(4f, 10f, 4f, 14f);
        com.badlogic.gdx.scenes.scene2d.utils.Drawable sunIcon = art.drawable(UiArt.SUN);
        if (sunIcon != null) {
            counters.add(new com.badlogic.gdx.scenes.scene2d.ui.Image(sunIcon)).size(38f, 38f)
                    .padRight(6f);
        }
        counters.add(sunLabel).width(64f).left();

        root.add(counters).left().row();
        root.add(cardRow).left().padTop(6f).row();

        Table toolbar = new Table();
        toolbar.add(shovelButton).padRight(6f);
        toolbar.add(plantFoodButton).padRight(10f);
        // Plant food is a count, not a tool state, so it reads as a number next to its own button.
        toolbar.add(plantFoodLabel);
        root.add(toolbar).left().padTop(6f);

        stage.addActor(root);

        // The wave meter sits along the bottom, clear of the toasts in the top-right corner.
        Table waveRow = new Table();
        waveRow.setFillParent(true);
        // Padded clear of the bottom edge: the zombie head is drawn taller than the meter and overhangs
        // it, so a flush-bottom meter loses the head off the screen.
        waveRow.bottom().pad(26f);
        // Bar first, count underneath it: the bar is the thing being read and the number annotates it.
        waveRow.add(waveBar).size(440f, 30f).row();
        waveRow.add(waveLabel).padTop(2f);
        stage.addActor(waveRow);
    }

    private Table buildPauseOverlay() {
        Table overlay = new Table();
        overlay.setFillParent(true);
        overlay.setBackground(assets.solid(DIM));

        Label title = new Label("Paused", assets.skin());
        title.setAlignment(Align.center);
        overlay.add(title).padBottom(10f).row();

        Label hint = new Label("P or Space to resume    -    Esc drops the held tool",
                assets.skin());
        hint.setAlignment(Align.center);
        overlay.add(hint);
        return overlay;
    }

    public void setPaused(boolean paused) {
        pauseOverlay.setVisible(paused);
    }

    // Pulled every frame from live state rather than pushed on change: there is no change notification
    // for sun, and polling four numbers is far cheaper than maintaining one.
    public void update(long currentTick) {
        sunLabel.setText(String.valueOf(session.getSunAmount()));
        plantFoodLabel.setText(String.valueOf(session.getPlantFoodCount()));

        int sun = session.getSunAmount();
        for (SeedCardActor card : cards) {
            card.refresh(currentTick, sun);
            card.setSelected(tools.tool() == ToolState.Tool.SEED
                    && card.plantType().equalsIgnoreCase(tools.seedName()));
        }

        // Armed tools light up rather than swapping background art, so the panel stays consistent.
        shovelButton.setColor(tools.tool() == ToolState.Tool.SHOVEL ? TOOL_ARMED : Color.WHITE);
        plantFoodButton.setColor(
                tools.tool() == ToolState.Tool.PLANT_FOOD ? TOOL_ARMED : Color.WHITE);

        updateWave();
    }

    private void updateWave() {
        int total = session.getLevel() == null ? 0 : session.getLevel().getWaveCount();
        int current = session.getCurrentWave();
        if (total <= 0) {
            waveLabel.setText("");
            waveBar.set(0f, 0);
            return;
        }
        waveLabel.setText("Wave " + Math.min(current, total) + " / " + total);
        waveBar.set(Math.min(1f, current / (float) total), total);
    }

    // Centre of the first seed card in stage coordinates. Only -Dpvz.inputCheck uses this, to confirm
    // that a click on the bank is consumed by the HUD and never reaches the lawn behind it.
    public com.badlogic.gdx.math.Vector2 firstCardCentre() {
        if (cards.isEmpty()) {
            return null;
        }
        SeedCardActor card = cards.get(0);
        return card.localToStageCoordinates(
                new com.badlogic.gdx.math.Vector2(card.getWidth() / 2f, card.getHeight() / 2f));
    }

    public void render(float delta) {
        stage.getViewport().apply();
        stage.act(delta);
        stage.draw();
    }

    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void dispose() {
        stage.dispose();
    }
}
