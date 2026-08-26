package views.gdx.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
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

    private final Assets assets;
    private final UiArt art;
    private final GameSession session;
    private final ToolState tools;
    private final Stage stage;

    private final Label sunLabel;
    private final Label plantFoodLabel;
    private final Label waveLabel;
    // The scoring game's running Meow Point total. Built unconditionally and only ADDED to the layout
    // on a scoring board, so nothing else has to know whether it is on screen.
    private final Label meowLabel;
    private final Table cardRow;
    private final Table shovelButton;
    private final Table plantFoodButton;
    private final WaveBar waveBar;
    // Coins and gems during a level. The spec asks for both to be visible "in all menus, even
    // during gameplay", and until now the lawn was the one screen without them -- which also left
    // a dropped coin with no counter to land on.
    private final CurrencyHUD wallet;
    // The four things a drop can fly to, kept so PickupFlights can ask where each one is and
    // bounce it. Sun is here for symmetry even though nothing flies to it yet.
    private final java.util.Map<PickupKind, Actor> counterTargets =
            new java.util.EnumMap<>(PickupKind.class);

    private final List<SeedCardActor> cards = new ArrayList<>();

    // Where a debug "+" posts. Assigned by installCheats, which is the first moment the engine exists;
    // the buttons are laid out before that, so they read the field when CLICKED rather than capturing
    // it when built. Null until then, and a click before the engine is wired simply does nothing.
    private java.util.function.Predicate<String> cheatSink;
    private java.util.function.IntConsumer coinCheat;
    private java.util.function.IntConsumer gemCheat;

    // The currency half of the debug controls. Null-safe: with Debug Mode off the buttons were never
    // built, so nothing ever calls these.
    public void installCurrencyCheats(java.util.function.IntConsumer addCoins,
                                      java.util.function.IntConsumer addGems) {
        this.coinCheat = addCoins;
        this.gemCheat = addGems;
    }

    private boolean debugMode() {
        return session.getPlayer() != null && session.getPlayer().isDebugMode();
    }

    private void cheat(String command) {
        if (cheatSink != null) {
            cheatSink.test(command);
        }
    }

    public GameHud(Assets assets, SpriteRegistry sprites, GameSession session, ToolState tools) {
        this.assets = assets;
        this.art = new UiArt(assets);
        this.session = session;
        this.tools = tools;
        this.stage = new Stage(new FitViewport(PvZGame.VIRTUAL_WIDTH, PvZGame.VIRTUAL_HEIGHT));

        this.sunLabel = new Label("0", assets.skin());
        this.plantFoodLabel = new Label("0", assets.skin());
        this.waveLabel = new Label("", assets.skin());
        this.meowLabel = new Label("0", assets.skin());
        this.cardRow = new Table();
        this.waveBar = new WaveBar(assets, this.art);
        // Sun and plant food have in-game cheat commands; coins and gems do NOT -- the in-game grammar
        // is only `cheat add -n N suns` and `cheat add-plant-food`, so a command string for currency
        // would parse as nothing and the button would silently do nothing. Those two go out as a
        // CheatAddCommand instead, handed in by GameScreen, which is the same route every menu uses.
        //
        // Delegated through a mutable field rather than captured, because the wallet is laid out in this
        // constructor and the route only exists once the screen has finished building.
        this.wallet = debugMode()
                ? new CurrencyHUD(assets.skin(),
                        n -> { if (coinCheat != null) { coinCheat.accept(n); } },
                        n -> { if (gemCheat != null) { gemCheat.accept(n); } })
                : new CurrencyHUD(assets.skin());

        buildSeedBank(sprites);
        buildConveyor();
        buildRoster();
        this.shovelButton = toolButton(UiArt.SHOVEL_ICON, "Shovel [S]", ToolState.Tool.SHOVEL);
        this.plantFoodButton = toolButton(UiArt.PLANTFOOD_BUTTON, "Food [F]",
                ToolState.Tool.PLANT_FOOD);

        layoutRoot();

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
        // Kept whether or not the panel is rebuilt: the currency, sun and plant-food "+" buttons post
        // through this too, and they are laid out long before this is called.
        this.cheatSink = sink;
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

    // Beghouled's upgrade shop. Null on every other board, and installed rather than built in the
    // constructor because it needs somewhere to post its commands.
    private UpgradePanel upgrades;

    public void installUpgrades(java.util.function.Predicate<String> onUpgrade) {
        if (upgrades != null || UpgradePanel.modeOf(session) == null) {
            return;
        }
        upgrades = new UpgradePanel(assets, art, session, onUpgrade);
        Table holder = new Table();
        holder.setFillParent(true);
        // BOTTOM-right, and pushed as far down as it goes. Six rows is a tall panel: middle-right, where
        // the cheat panel sits, ran its top two rows up behind the toast stack -- and on this board that
        // stack is never empty, because every refused swap says so. Measured rather than guessed: the
        // panel is ~474 stage units and five toasts are ~230, which is 704 of the 720 available, so it
        // only clears the toasts if it starts from the floor. The wave meter is centred along the
        // bottom and 440 wide, so it does not reach this corner.
        holder.right().bottom().padRight(12f).padBottom(18f);
        holder.add(upgrades);
        stage.addActor(holder);
    }

    public void toggleCheats() {
        if (cheatHolder != null) {
            cheatHolder.setVisible(!cheatHolder.isVisible());
        }
    }

    // The debug spawner. Added to the stage DIRECTLY rather than into a fillParent Table like the cheat
    // panel: a Table re-places its children on every layout pass, which would drag the window straight
    // back to its anchor the moment it was moved.
    private ZombieSpawnerWindow spawner;

    public void installSpawner(java.util.function.Predicate<String> sink) {
        if (spawner != null) {
            return;
        }
        spawner = new ZombieSpawnerWindow(assets, sink);
        // Left of centre and high, clear of the seed bank along the top and of the cheat panel on the
        // right -- the two things a tester has open at the same time as this.
        spawner.setPosition(118f, PvZGame.VIRTUAL_HEIGHT - spawner.getHeight() - 150f);
        spawner.setVisible(false);
        stage.addActor(spawner);
    }

    public ZombieSpawnerWindow spawner() {
        return spawner;
    }

    public void toggleSpawner() {
        if (spawner != null) {
            spawner.setVisible(!spawner.isVisible());
            // In front of the seed bank and the cheat panel, both of which are added later than it is.
            spawner.toFront();
        }
    }

    // One card per packet the player actually brought. Read once: the bank does not change mid-level
    // (modes that adjust the slots do so in onStart, before the first frame).
    //
    // Unless the mode never asked the player to pick a loadout. Vasebreaker's supply comes out of broken
    // vases, Wall-nut Bowling's off a belt, I-Zombie's off a roster -- none of them has a bank of
    // packets to build here, and each fills the same slot its own way afterwards.
    //
    // Asked through requiresSeedSelection() rather than by naming those modes: it is the same question
    // the seed-selection screen is gated on, so a bank can never appear for a level nobody picked seeds
    // for. Before this, DevBoot's convenience loadout put six plant cards over Wall-nut Bowling's belt.
    private void buildSeedBank(SpriteRegistry sprites) {
        this.sprites = sprites;
        List<SeedPacket> packets = session.getSelectedSeeds();
        if (packets == null || session.getMode() == null
                || !session.getMode().requiresSeedSelection(session)) {
            return;
        }
        for (SeedPacket packet : packets) {
            SeedCardActor card = new SeedCardActor(assets, art, packet, costOf(packet.getPlantType()),
                    new PlantIcon(sprites.get(packet.getPlantType())));
            // Locked Plants bolts seeds to the bar. The badge carries over from seed selection so a
            // packet means the same thing in both places -- asked through the GameMode hook, so any
            // future mode that pins a seed gets the same mark without this line changing.
            card.setLocked(session.getMode() != null
                    && session.getMode().isSeedForced(packet.getPlantType()));
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

    // Kept so the hand can be rebuilt after the constructor has run.
    private SpriteRegistry sprites;

    // ---- Wall-nut Bowling's conveyor --------------------------------------------------------------

    // Null on every other level. Non-null, it is added to the stage as its own left-edge column.
    private Table conveyor;

    // A VERTICAL belt down the left-hand side, nuts riding UP it.
    //
    // A vertical belt is a stack of horizontal slats, which is exactly what the shipped
    // CONVEYOR_BELT strip is one of -- so the belt is built by repeating that strip once per slot
    // rather than by rotating a single copy, which Scene2D cannot do to an Image without a transform
    // group around it.
    //
    // Order matters and is not arbitrary: getConveyor() appends new nuts, so index 0 is the OLDEST and
    // has therefore ridden furthest -- it goes at the TOP. Adding the list downward puts the newest at
    // the bottom, where the next one will appear.
    private static final float BELT_SLOT_WIDTH = 66f;
    private static final float BELT_SLOT_HEIGHT = 80f;
    private static final float BELT_TOP_HEIGHT = 12f;
    private static final float BELT_PAD = 6f;

    private String conveyorSignature = "";

    private void buildConveyor() {
        models.game.gamemodes.WallnutBowlingMode mode =
                views.gdx.render.BowlingRenderer.modeOf(session);
        if (mode == null) {
            return;
        }
        Table column = new Table();
        column.top();
        addBeltPiece(column, UiArt.CONVEYOR_TOP, BELT_SLOT_WIDTH + BELT_PAD * 2f, BELT_TOP_HEIGHT);

        // One slat per slot the belt can hold, always -- never per nut currently on it. A belt that
        // shrank as nuts were taken would read as the belt itself being consumed, and the empty run
        // below the last nut is the useful part: it is how much more is coming.
        cardRow.top();
        Stack track = new Stack();
        // One scrolling actor rather than a column of static Images -- see ConveyorBelt. A belt that
        // does not move is the one thing a conveyor must not be.
        com.badlogic.gdx.scenes.scene2d.utils.Drawable slatArt = art.drawable(UiArt.CONVEYOR_BELT);
        if (slatArt != null) {
            Table slats = new Table();
            slats.add(new ConveyorBelt(slatArt, BELT_SLOT_HEIGHT))
                    .size(BELT_SLOT_WIDTH + BELT_PAD * 2f,
                            BELT_SLOT_HEIGHT * mode.conveyorCapacity());
            track.add(slats);
        }
        track.add(cardRow);
        column.add(track).width(BELT_SLOT_WIDTH + BELT_PAD * 2f).row();

        conveyor = new Table();
        conveyor.setFillParent(true);
        // Left edge, vertically centred, clear of the sun counter in the corner.
        conveyor.left().padLeft(10f).padTop(90f);
        conveyor.add(column).top();
    }

    // A missing piece costs the belt its art, not the player their nuts.
    private void addBeltPiece(Table into, String regionId, float width, float height) {
        com.badlogic.gdx.scenes.scene2d.utils.Drawable piece = art.drawable(regionId);
        if (piece != null) {
            into.add(new com.badlogic.gdx.scenes.scene2d.ui.Image(piece,
                    com.badlogic.gdx.utils.Scaling.stretch)).size(width, height).row();
        }
    }

    // The nuts currently on the belt, in belt order.
    //
    // Rebuilt on change for the same reason Vasebreaker's hand is: the conveyor is a LIST, not a set of
    // counts -- the mode delivers a nut every five seconds and removes the one you bowl -- so positions
    // shift and a card cannot simply be updated in place. The signature check is what keeps that from
    // happening sixty times a second, which would hand every click an actor that no longer exists.
    private void refreshConveyor() {
        models.game.gamemodes.WallnutBowlingMode mode =
                views.gdx.render.BowlingRenderer.modeOf(session);
        if (mode == null) {
            return;
        }
        java.util.List<models.entities.plants.bowling.BowlingKind> belt = mode.getConveyor();
        String signature = String.valueOf(belt);
        if (signature.equals(conveyorSignature)) {
            return;
        }
        conveyorSignature = signature;

        cards.clear();
        cardRow.clearChildren();
        for (models.entities.plants.bowling.BowlingKind kind : belt) {
            // A ROW each, not a column: this belt runs top to bottom.
            cardRow.add(nutCard(kind)).size(BELT_SLOT_WIDTH, BELT_SLOT_HEIGHT).row();
        }
    }

    // ---- I, Zombie's roster -----------------------------------------------------------------------

    // The zombies this level lets you buy, with what each costs in sun.
    //
    // Built once, unlike the belt and the hand: IZombieMode.buildRoster runs in onStart and the roster
    // never changes afterwards. What DOES change is what you can afford, and that is already
    // SeedCardActor.refresh's job -- the same dim it puts on a plant you have no sun for.
    private void buildRoster() {
        models.game.gamemodes.IZombieMode mode = views.gdx.render.IZombieRenderer.modeOf(session);
        if (mode == null) {
            return;
        }
        for (java.util.Map.Entry<String, Integer> entry : mode.getRoster().entrySet()) {
            cardRow.add(rosterCard(entry.getKey(), entry.getValue()))
                    .size(SeedCardActor.CARD_WIDTH, SeedCardActor.CARD_HEIGHT).padRight(6f);
        }
    }

    private SeedCardActor rosterCard(String alias, int price) {
        views.gdx.sprite.EntitySprite sprite = sprites.get(alias);
        // With its armour switched on. Conehead, Buckethead and Brick are ONE animation with three
        // hideable hats, so a roster built without this offers three identical bare zombies at 50, 150
        // and 175 sun -- see ArmorVisibility.
        EntityIcon icon = new EntityIcon(sprite)
                .showing(views.gdx.sprite.ArmorVisibility.forAlias(alias, sprite));
        // The alias is the packet type too, which is deliberate: art.packet finds no zombie packet, so
        // the card falls through to the live animation above rather than drawing a plant's portrait.
        SeedCardActor card = new SeedCardActor(assets, art, new SeedPacket(alias, 0), price, icon);
        card.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                tools.selectZombie(alias);
            }
        });
        cards.add(card);
        return card;
    }

    private SeedCardActor nutCard(models.entities.plants.bowling.BowlingKind kind) {
        String packetPlant = views.gdx.render.BowlingRenderer.packetPlantFor(kind);
        String token = views.gdx.render.BowlingRenderer.tokenFor(kind);
        // No price and no count: a nut off the belt is free, and one card on the belt IS one nut, so
        // "x1" under every single card would say nothing the belt does not already show.
        SeedCardActor card = new SeedCardActor(assets, art, new SeedPacket(packetPlant, 0), 0,
                new PlantIcon(sprites.get(views.gdx.render.BowlingRenderer.spriteFor(kind))));
        card.setSupply(0);
        card.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                tools.selectNut(token);
            }
        });
        cards.add(card);
        return card;
    }

    // What the hand looked like last time it was drawn, so the bank is rebuilt only when it changes.
    private String handSignature = "";

    // Vasebreaker's bank: whatever the player is currently holding, straight off the mode.
    //
    // Rebuilt rather than updated because the SET changes, not just the counts -- a vase breaking adds
    // a plant type that was not there a moment ago, and placing the last one takes it away again. The
    // signature check is what stops that being a rebuild every frame; without it the cards would be
    // discarded and recreated sixty times a second and a click would land on an actor that no longer
    // exists.
    private void refreshHand() {
        if (session.getMode() == null || !session.getMode().managesPlantInventory()) {
            return;
        }
        java.util.Map<String, Integer> hand = session.getMode().plantInventory();
        String signature = String.valueOf(hand);
        if (signature.equals(handSignature)) {
            return;
        }
        handSignature = signature;

        cards.clear();
        cardRow.clearChildren();
        for (java.util.Map.Entry<String, Integer> held : hand.entrySet()) {
            if (held.getValue() == null || held.getValue() <= 0) {
                continue;
            }
            cardRow.add(handCard(held.getKey(), held.getValue()))
                    .size(SeedCardActor.CARD_WIDTH, SeedCardActor.CARD_HEIGHT).padRight(6f);
        }
    }

    private SeedCardActor handCard(String plantType, int count) {
        // A packet out of a vase has no recharge and no price: it is used once and it is gone. The
        // SeedPacket here exists only to carry the plant's name into the card.
        SeedCardActor card = new SeedCardActor(assets, art, new SeedPacket(plantType, 0),
                costOf(plantType), new PlantIcon(sprites.get(plantType)));
        card.setSupply(count);
        card.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                tools.selectSeed(card.plantType());
            }
        });
        cards.add(card);
        return card;
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

    // Sun and plant food are the two things a tester runs out of, and both already have an in-game
    // cheat command. Offered beside the number they change rather than only in the cheat panel, which
    // covers the lawn and has to be opened with a key; a "+" next to a counter needs no explaining.
    //
    // Lifted out of layoutRoot, which hit Checkstyle's 50-line method ceiling the moment they went in.
    private void addSunCheat(Table counters) {
        if (debugMode()) {
            counters.add(CurrencyHUD.plusButton(assets.skin(),
                    () -> cheat("cheat add -n 500 suns"))).padLeft(4f);
        }
    }

    private void addPlantFoodCheat(Table toolbar) {
        if (debugMode()) {
            toolbar.add(CurrencyHUD.plusButton(assets.skin(),
                    () -> cheat("cheat add-plant-food"))).padLeft(6f);
        }
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
        addSunCheat(counters);

        // The scoring game's running total, beside the sun and on no other board.
        //
        // Meow Points are the only thing that run is judged on, and without this the player has no idea
        // how they are doing until the level is over -- which makes every mid-run decision (spend the
        // sun or hoard it, save the mower or let it go) a guess. Added here rather than as its own
        // panel so the two numbers the mode is actually about sit together.
        if (session.getMode() instanceof models.game.gamemodes.ScoringMode) {
            counters.add(new Label("Meow", assets.skin())).padLeft(14f).padRight(6f);
            counters.add(meowLabel).width(64f).left();
        }

        // Sun on the left of the row, wallet on the right of it: one panel, so the numbers a player
        // checks mid-level are one glance rather than two corners.
        counters.add(wallet).padLeft(18f);

        counterTargets.put(PickupKind.PLANT_FOOD, plantFoodLabel);
        counterTargets.put(PickupKind.COIN, wallet.coinIcon());
        counterTargets.put(PickupKind.GEM, wallet.gemIcon());
        counterTargets.put(PickupKind.POT, wallet.coinIcon());

        root.add(counters).left().row();
        // Wall-nut Bowling's belt is NOT in this column: it runs vertically down the left edge and is
        // added to the stage on its own below, so the toolbar keeps its place under the counters
        // instead of being pushed off the bottom by a 480-unit-tall belt.
        if (conveyor == null) {
            root.add(cardRow).left().padTop(6f).row();
        }

        // Neither tool means anything on a Beghouled board, and the shovel is actively harmful there:
        // its plants are the match-3 board, and digging one out leaves a cell the mode's own
        // markEatenPlantsAsCraters then turns into a permanent CRATER on the next tick -- a tile
        // destroyed by a gesture that looks like tidying up. Offering a button whose only effect is to
        // damage the board is the view promising something it should not.
        if (UpgradePanel.modeOf(session) == null) {
            Table toolbar = new Table();
            toolbar.add(shovelButton).padRight(6f);
            toolbar.add(plantFoodButton).padRight(10f);
            // Plant food is a count, not a tool state, so it reads as a number beside its own button.
            toolbar.add(plantFoodLabel);
            addPlantFoodCheat(toolbar);
            root.add(toolbar).left().padTop(6f);
        }

        stage.addActor(root);
        if (conveyor != null) {
            stage.addActor(conveyor);
        }

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

    // The pause panel used to live here as a dimmed label. It is now GameOverlays, on this same Stage:
    // once pausing gained Restart and Save-and-Exit buttons it stopped being a caption and became one
    // of three interruption panels that all look and behave alike.

    // Pulled every frame from live state rather than pushed on change: there is no change notification
    // for sun, and polling four numbers is far cheaper than maintaining one.
    public void update(long currentTick) {
        refreshHand();
        refreshConveyor();
        sunLabel.setText(String.valueOf(session.getSunAmount()));
        plantFoodLabel.setText(String.valueOf(session.getPlantFoodCount()));
        wallet.refresh(session.getPlayer());
        if (session.getMode() instanceof models.game.gamemodes.ScoringMode scoring) {
            meowLabel.setText(String.valueOf(scoring.getMeowPoints().getTotal()));
        }

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

        if (upgrades != null) {
            upgrades.refresh();
        }
        updateWave();
    }

    private void updateWave() {
        // Beghouled counts MATCHES, not waves: its zombies arrive on a timer with no wave structure at
        // all, so the meter along the bottom read "" and sat empty for the whole level -- with the one
        // number that decides when the level ends printed once, in the opening banner, and never again.
        // Same widget, because it is the same question: how far through am I?
        models.game.gamemodes.BeghouledMode beghouled = UpgradePanel.modeOf(session);
        if (beghouled != null && beghouled.getMatchTarget() > 0) {
            int made = Math.min(beghouled.getMatchesMade(), beghouled.getMatchTarget());
            waveLabel.setText("Matches " + made + " / " + beghouled.getMatchTarget());
            waveBar.set(made / (float) beghouled.getMatchTarget(), beghouled.getMatchTarget());
            return;
        }
        int total = session.getLevel() == null ? 0 : session.getLevel().getWaveCount();
        int current = session.getCurrentWave();
        if (total <= 0) {
            waveLabel.setText("");
            waveBar.set(0f, 0);
            return;
        }
        waveLabel.setText("Wave " + Math.min(current, total) + " / " + total);
        waveBar.set(waveProgress(current, total), total);
    }

    // The arithmetic lives in WaveProgress, which is where its reasoning is written down; this only
    // fetches the one number the model has to be asked for.
    private float waveProgress(int current, int total) {
        return WaveProgress.of(current, total, clearedFractionOf(current));
    }

    private float clearedFractionOf(int waveNumber) {
        models.game.Level level = session.getLevel();
        models.game.Wave[] waves = level == null ? null : level.getWaves();
        int index = waveNumber - 1;
        if (waves == null || index < 0 || index >= waves.length || waves[index] == null) {
            return 1f;   // past the end of the roster: the level is over, so the bar is full
        }
        return (float) waves[index].hpLostFraction();
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

    // Where a flying pickup should land, in stage coordinates, and what to bounce when it gets there.
    //
    // The ACTOR rather than a position: a counter moves when the seed bank rebuilds or the wallet's
    // digits widen, and a position cached at build time would send later drops to where the counter
    // used to be.
    public Actor counterTarget(PickupKind kind) {
        return counterTargets.get(kind);
    }

}
