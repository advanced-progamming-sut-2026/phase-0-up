package views.gdx.screens;

import com.badlogic.gdx.Gdx;
import controllers.engine.GameEngine;
import models.game.GameSession;
import views.gdx.core.GdxContext;
import views.gdx.input.LawnInputProcessor;
import views.gdx.input.ToolState;
import views.gdx.map.LawnGeometry;
import views.gdx.ui.GameHud;

// -Dpvz.inputCheck=1, and the four hand-built scenarios that ride along with it.
//
// Everything here answers a question a screenshot cannot: does a click on tile (3, 2) actually reach
// tile (3, 2) through a live viewport, does the HUD really get first refusal on a click over a seed
// card, and do three specific plant-food interactions behave the way their animations claim. None of it
// runs in a normal game.
//
// Lifted out of GameScreen for the same reason MinigameHarness was: that class is at Checkstyle's
// 500-NCSS ceiling and this is 250 lines of instrument rather than screen. It is a clean seam anyway --
// none of it draws anything, and all of it drives the screen from outside.
//
// It needs a lot of the screen's collaborators because that is the point: a check that reached past
// them to the model would not be checking the path a click actually takes.
final class InputCheck {

    private final GdxContext context;
    private final GameSession session;
    private final GameEngine engine;
    private final LawnGeometry lawn;
    private final com.badlogic.gdx.utils.viewport.Viewport viewport;
    private final LawnInputProcessor lawnInput;
    private final ToolState tools;
    private final GameHud hud;
    // Where a synthesised event goes when a check swaps the in-game renderer out for its own. Handed in
    // rather than reached for, so the screen keeps the single onModelEvent fan-out it documents.
    private final java.util.function.Consumer<String> onModelEvent;

    InputCheck(GdxContext context, GameSession session, GameEngine engine, LawnGeometry lawn,
               com.badlogic.gdx.utils.viewport.Viewport viewport, LawnInputProcessor lawnInput,
               ToolState tools, GameHud hud, java.util.function.Consumer<String> onModelEvent) {
        this.context = context;
        this.session = session;
        this.engine = engine;
        this.lawn = lawn;
        this.viewport = viewport;
        this.lawnInput = lawnInput;
        this.tools = tools;
        this.hud = hud;
        this.onModelEvent = onModelEvent;
    }

    // Viewport.project returns y measured UP from the bottom (OpenGL's convention), while unproject --
    // and every real mouse event -- measures y DOWN from the top. LibGDX does not make the pair
    // symmetric, so the flip has to happen here. Without it this check reports all 45 tiles as wrong
    // and blames the input processor, which is reading the axis correctly.
    private int toMouseY(float projectedY) {
        return (int) (Gdx.graphics.getHeight() - projectedY);
    }

    private int countProjectiles() {
        int total = 0;
        for (models.map.Row row : session.getMap().getRows()) {
            total += row.getActiveProjectiles().size();
        }
        return total;
    }

    void run() {
        com.badlogic.gdx.math.Vector3 point = new com.badlogic.gdx.math.Vector3();
        int mismatches = 0;
        for (int row = 0; row < utils.Constants.BOARD_ROWS; row++) {
            for (int col = 0; col < utils.Constants.BOARD_COLS; col++) {
                point.set(lawn.centerX(col), lawn.centerY(row), 0f);
                viewport.project(point);
                lawnInput.mouseMoved((int) point.x, toMouseY(point.y));

                views.gdx.map.GridPos got = lawnInput.hovered();
                if (got.col() != col || got.row() != row) {
                    mismatches++;
                    Gdx.app.error("InputCheck", "tile (" + col + ", " + row + ") -> screen "
                            + (int) point.x + "," + toMouseY(point.y) + " -> " + got);
                }
            }
        }
        Gdx.app.log("InputCheck", mismatches == 0
                ? "all " + (utils.Constants.BOARD_COLS * utils.Constants.BOARD_ROWS)
                        + " tiles round-tripped correctly"
                : mismatches + " tiles did NOT round-trip");

        checkToolActions(point);
        checkHudClaimsItsOwnClicks();
    }

    // The HUD is first in the InputMultiplexer so a click on a seed card cannot also plant on the tile
    // behind it. That is an ordering claim, and ordering claims are exactly the kind that survive review
    // and then quietly stop being true, so it is asserted rather than trusted.
    private void checkHudClaimsItsOwnClicks() {
        com.badlogic.gdx.math.Vector2 centre = hud.firstCardCentre();
        if (centre == null) {
            return;
        }
        com.badlogic.gdx.math.Vector3 point =
                new com.badlogic.gdx.math.Vector3(centre.x, centre.y, 0f);
        hud.stage().getViewport().project(point);
        int screenX = (int) point.x;
        int screenY = toMouseY(point.y);

        tools.clear();
        boolean consumed = hud.stage().touchDown(screenX, screenY, 0, com.badlogic.gdx.Input.Buttons.LEFT);
        hud.stage().touchUp(screenX, screenY, 0, com.badlogic.gdx.Input.Buttons.LEFT);

        Gdx.app.log("InputCheck", "seed card click: consumed=" + consumed
                + " armed=" + tools.tool() + " seed=" + tools.seedName()
                + (consumed ? "" : "  <-- would fall through to the lawn"));

        // Leaves the shovel armed with the cursor still parked from checkToolActions, so pairing this
        // flag with -Dpvz.screenshot captures the held-tool cursor and the tile highlight. Clearing
        // here instead is what made both of them invisible in the capture.
        tools.selectTool(views.gdx.input.ToolState.Tool.SHOVEL);

        // Plant food, end to end: feed the Peashooter at (1, 0) and count the shots it produces. The
        // effect is a queued burst, so a working feed shows up as projectiles appearing immediately.
        engine.submitInGameCommand("cheat add-plant-food");
        int shotsBefore = countProjectiles();
        boolean fedOk = engine.submitInGameCommand("feed plant -l (1, 0)");
        models.map.Cell fedCell = session.getMap().getRow(0).cellAt(1);
        // Ticks have to elapse: queueBurst only queues, and the shots come out over later updates.
        // Counting immediately after the feed reports 0 and looks like the burst did nothing.
        engine.submitInGameCommand("advance time -t 20 ticks");
        Gdx.app.log("InputCheck", "feed accepted=" + fedOk
                + " plant=" + (fedCell.hasPlant() ? fedCell.getCurrentPlant().getName() : "EMPTY")
                + " hasFood=" + (fedCell.hasPlant() && fedCell.getCurrentPlant().hasPlantFood())
                + " active=" + (fedCell.hasPlant() && fedCell.getCurrentPlant().isPlantFoodActive())
                + " food-left=" + session.getPlantFoodCount()
                + " shots " + shotsBefore + " -> " + countProjectiles());

        checkSnowPeaPlantFood();
        checkFeedWhileShooting();

        checkWallnutPlantFood();

        // A detonation, so the two-layer explosion can be seen at all: nothing in a normal opening
        // minute blows up, and the effect is driven entirely by the model's narration.
        boolean bomb = engine.submitInGameCommand("plant plant -t Cherry Bomb -l (5, 2)");
        models.map.Cell bombCell = session.getMap().getRow(2).cellAt(5);
        Gdx.app.log("InputCheck", "cherry bomb command accepted: " + bomb
                + " cell now: " + (bombCell.hasPlant()
                        ? bombCell.getCurrentPlant().getName() : "EMPTY"));
        if (bombCell.hasPlant()) {
            models.entities.plants.Plant bombPlant = bombCell.getCurrentPlant();
            StringBuilder abilities = new StringBuilder();
            if (bombPlant.getAbilities() != null) {
                for (models.entities.plants.abilities.PlantAbility a : bombPlant.getAbilities()) {
                    abilities.append(a.getClass().getSimpleName()).append(' ');
                }
            }
            Gdx.app.log("InputCheck", "  bomb: dead=" + bombPlant.isDead()
                    + " disabled=" + bombPlant.isDisabled()
                    + " abilities=[" + abilities.toString().trim() + "]");
        }
    }

    // The whole chain end to end: arm a seed, click a tile, see whether a plant is standing on it
    // afterwards -- then shovel it out again, then collect a sun by hovering.
    private void checkToolActions(com.badlogic.gdx.math.Vector3 point) {
        if (session.getSelectedSeeds() != null && !session.getSelectedSeeds().isEmpty()) {
            // Sun and cooldown are both topped up first. The level pre-plants a Sunflower, which puts
            // that packet straight onto its 5s recharge -- a real rule, correctly enforced, but it would
            // make this check report a failure that is nothing to do with the input path.
            engine.submitInGameCommand("cheat add -n 5000 suns");
            engine.submitInGameCommand("cheat remove-cooldown");
            String seed = session.getSelectedSeeds().get(0).getPlantType();
            int col = utils.Constants.BOARD_COLS - 2;
            int row = 0;

            // Log every Result the click produces instead of toasting it: a refusal ("there is already
            // a plant there", "that seed is recharging") is the whole answer when this reports NOTHING,
            // and a toast in a headless smoke run goes nowhere anyone can read it.
            engine.setInGameRenderer(new views.renderers.InGameRenderer() {
                @Override
                public void render(utils.Result result) {
                    Gdx.app.log("InputCheck", "  engine said: " + result.message());
                }
            });
            tools.selectSeed(seed);
            point.set(lawn.centerX(col), lawn.centerY(row), 0f);
            viewport.project(point);
            lawnInput.touchDown((int) point.x, toMouseY(point.y), 0,
                    com.badlogic.gdx.Input.Buttons.LEFT);

            models.map.Cell cell = session.getMap().getRow(row).cellAt(col);
            Gdx.app.log("InputCheck", "click-to-plant " + seed + " at (" + col + ", " + row + "): "
                    + (cell.hasPlant() ? "PLANTED " + cell.getCurrentPlant().getName() : "NOTHING"));

            // Shovel the same tile back out, so the other half of the tool path is covered too.
            tools.selectTool(views.gdx.input.ToolState.Tool.SHOVEL);
            lawnInput.touchDown((int) point.x, toMouseY(point.y), 0,
                    com.badlogic.gdx.Input.Buttons.LEFT);
            Gdx.app.log("InputCheck", "shovel at (" + col + ", " + row + "): "
                    + (cell.hasPlant() ? "STILL THERE" : "CLEARED"));

            // Hover-to-collect: drop a sun on a known tile, move the cursor onto it, expect the wallet
            // to grow and the sun to leave the board.
            int before = session.getSunAmount();
            int sunCol = 2;
            int sunRow = 3;
            session.addSun(new models.entities.collectibles.Sun(
                    sunCol + 0.5, sunRow, sunRow,
                    models.entities.collectibles.SunType.NORMAL, 25, false, 100));
            point.set(lawn.centerX(sunCol), lawn.centerY(sunRow), 0f);
            viewport.project(point);
            tools.clear();
            lawnInput.touchDown((int) point.x, toMouseY(point.y), 0,
                    com.badlogic.gdx.Input.Buttons.LEFT);
            Gdx.app.log("InputCheck", "click-collect: sun " + before + " -> "
                    + session.getSunAmount());

            engine.setInGameRenderer(new views.gdx.renderers.GdxInGameRenderer(context.toasts(),
                    onModelEvent::accept));

            // Leave the shovel armed with the cursor parked on a tile, so pairing this flag with
            // -Dpvz.screenshot captures the hover highlight -- the one part of the input work that can
            // only be checked by looking.
            tools.selectTool(views.gdx.input.ToolState.Tool.SHOVEL);
            point.set(lawn.centerX(4), lawn.centerY(2), 0f);
            viewport.project(point);
            lawnInput.mouseMoved((int) point.x, toMouseY(point.y));
        }
    }

    // Does Snow Pea's plant food actually do anything? Its two effects are a lane freeze and a shot
    // burst, and NEITHER has art wired up yet -- so "I don't see it apply" could equally mean it is
    // firing invisibly. Chill is the model's own record of the freeze landing, so it settles the
    // question without needing to see anything.
    private void checkSnowPeaPlantFood() {
        int[] found = findPlant("Snow Pea");
        if (found == null) {
            Gdx.app.log("SnowPea", "no Snow Pea on the board to feed");
            return;
        }
        int col = found[0];
        int row = found[1];

        // Something to freeze, parked in the same lane.
        engine.submitInGameCommand("cheat spawn-zombie -t normal -l (8, " + row + ")");
        engine.submitInGameCommand("cheat add-plant-food");
        boolean fed = engine.submitInGameCommand("feed plant -l (" + col + ", " + row + ")");

        // Ticks have to actually elapse for the strategy to run and the chill to be stamped on.
        engine.submitInGameCommand("advance time -t 20 ticks");

        int chilled = 0;
        int total = 0;
        for (models.entities.zombies.Zombie z : session.getMap().getRow(row).getZombies()) {
            total++;
            if (z.getState().isChilled() || z.getState().getChilledTimer() > 0) {
                chilled++;
            }
        }
        Gdx.app.log("SnowPea", "fed=" + fed + " at (" + col + ", " + row + ")"
                + "  zombies in lane=" + total + "  chilled=" + chilled
                + "  shots=" + countProjectiles());
    }

    // The case that actually broke: feeding a plant that is MID-WIND-UP, rather than idle. A zombie is
    // parked in the lane first so the shooter is genuinely firing, then the feed lands during the
    // wind-up window and the burst has to survive it.
    private void checkFeedWhileShooting() {
        // A FRESH plant, never fed. Reusing one from an earlier check leaves its previous burst still
        // running, which masks whether this feed survived.
        int col = 3;
        int row = 4;
        engine.submitInGameCommand("cheat add -n 500 suns");
        engine.submitInGameCommand("cheat remove-cooldown");
        engine.submitInGameCommand("plant plant -t Peashooter -l (" + col + ", " + row + ")");
        engine.submitInGameCommand("cheat spawn-zombie -t Basic -l (8, " + row + ")");

        models.map.Cell cell = session.getMap().getRow(row).cellAt(col);
        if (!cell.hasPlant()) {
            Gdx.app.log("FeedMidShot", "could not place a test Peashooter");
            return;
        }

        engine.submitInGameCommand("cheat add-plant-food");
        // Part-way into a wind-up: the shot is committed but has not left yet. This is the window that
        // used to swallow the burst.
        engine.submitInGameCommand("advance time -t 2 ticks");
        engine.submitInGameCommand("feed plant -l (" + col + ", " + row + ")");
        engine.submitInGameCommand("advance time -t 6 ticks");

        // The queue itself, not a projectile count: shots expire off the board, so counting them
        // undercounts a burst that is working.
        Gdx.app.log("FeedMidShot", "fed a winding-up Peashooter at (" + col + ", " + row + "): "
                + "burst still queued = " + cell.getCurrentPlant().isPlantFoodActive()
                + "  (false here is the bug)");
    }

    // Wall-nut is the plant that actually ships plantfood_on and plantfood_off, so it is the only way
    // to exercise the full three-stage sequence. Fed here and then left alone: the stages are chosen by
    // the RENDERER, so they only appear as the game draws frames -- advancing ticks synchronously would
    // run the whole boost past without a single clip ever being selected.
    private void checkWallnutPlantFood() {
        int[] nut = findPlant("Wall-nut");
        if (nut == null) {
            return;
        }
        engine.submitInGameCommand("cheat add-plant-food");
        boolean ok = engine.submitInGameCommand("feed plant -l (" + nut[0] + ", " + nut[1] + ")");
        Gdx.app.log("NutFood", "fed Wall-nut at (" + nut[0] + ", " + nut[1] + ") = " + ok
                + "; watch ClipChange for plantfood_on -> plantfood -> plantfood_off");
    }

    private int[] findPlant(String name) {
        for (int row = 0; row < utils.Constants.BOARD_ROWS; row++) {
            for (int col = 0; col < utils.Constants.BOARD_COLS; col++) {
                models.map.Cell cell = session.getMap().getRow(row).cellAt(col);
                if (cell.hasPlant() && name.equalsIgnoreCase(cell.getCurrentPlant().getName())) {
                    return new int[] {col, row};
                }
            }
        }
        return null;
    }

}
