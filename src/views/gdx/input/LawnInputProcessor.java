package views.gdx.input;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.Viewport;
import models.entities.collectibles.Sun;
import models.entities.interactables.Vase;
import models.game.GameSession;
import models.game.gamemodes.VaseBreakerMode;
import views.gdx.bridge.CommandBridge;
import views.gdx.map.GridPos;
import views.gdx.map.LawnGeometry;

// Mouse on the lawn: which tile is under the cursor, and what a click there means.
//
// Screen pixels are not world units -- the camera shows a 1365-wide slice of a 1975-wide background,
// letterboxed by a FitViewport -- so every position has to be unprojected before LawnGeometry can say
// which tile it is. Doing that through the viewport (rather than by hand) is what keeps the hit box
// welded to the art when the window is resized or letterboxed.
public final class LawnInputProcessor extends InputAdapter {

    private final Viewport viewport;
    private final GameSession session;
    private final CommandBridge commands;
    private final ToolState tools;
    private LawnGeometry lawn;

    // Reused: unprojecting allocates nothing this way, and this runs on every mouse-move event.
    private final Vector3 scratch = new Vector3();

    // The tile under the cursor, or an off-lawn GridPos when the mouse is elsewhere.
    private GridPos hovered = new GridPos(-1, -1);

    private final views.gdx.render.CollectibleRenderer collectibles;

    public LawnInputProcessor(Viewport viewport, LawnGeometry lawn, GameSession session,
                              CommandBridge commands, ToolState tools,
                              views.gdx.render.CollectibleRenderer collectibles) {
        this.viewport = viewport;
        this.lawn = lawn;
        this.session = session;
        this.commands = commands;
        this.tools = tools;
        this.collectibles = collectibles;
    }

    // Which side of a versus match this player is on, or null on a single-player board.
    //
    // Almost all of the role separation is already done by the HUD: the zombie player is given no seed
    // cards, no shovel and no plant food, so there is nothing to arm and the tool branches below can
    // never fire. Sun is the exception -- it is clicked directly off the lawn with nothing held, so
    // without this a zombie player clicking the plant player's sun would fire a command the server
    // correctly refuses, and collect a toast telling them so on every click.
    //
    // This is convenience, not the rule. The rule is FactionCommands, on the server.
    private models.game.Faction faction;

    public void setFaction(models.game.Faction faction) {
        this.faction = faction;
    }

    private boolean maySpendOnPlants() {
        return faction == null || faction == models.game.Faction.PLANTS;
    }

    public GridPos hovered() {
        return hovered;
    }

    // Last unprojected cursor position, in world coordinates. The cursor renderer draws the held item
    // here; it is the same point the tile lookup was derived from, so the art and the tile that will
    // actually be planted can never disagree.
    public float cursorWorldX() {
        return scratch.x;
    }

    public float cursorWorldY() {
        return scratch.y;
    }

    public void setLawn(LawnGeometry lawn) {
        this.lawn = lawn;
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        updateHover(screenX, screenY);
        return false;   // hovering never consumes the event; the HUD may want it too
    }

    // Dragging still counts as moving for hover purposes -- otherwise the highlight sticks to wherever
    // the button went down, which reads as the lawn having frozen.
    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        updateHover(screenX, screenY);
        return false;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        updateHover(screenX, screenY);

        // Suns are tested against WHERE THEY ARE DRAWN, before anything to do with tiles. A falling sun
        // is drawn several cells above the tile it is heading for, so a tile-based test would only
        // catch it after it landed -- the player could see it in the air but not click it.
        // scratch still holds the unprojected world point from updateHover.
        if (button == Input.Buttons.LEFT) {
            Sun airborne = maySpendOnPlants() ? collectibles.sunAt(scratch.x, scratch.y) : null;
            if (airborne != null && commands.collectSun(tileOf(airborne))) {
                return true;
            }
        }

        // Right-click drops whatever is held, wherever the cursor is. The original game uses this and
        // it is worth having: with a seed armed, every left click costs sun, so there has to be a way
        // out that is not "spend it".
        if (button == Input.Buttons.RIGHT) {
            boolean wasHolding = tools.isHolding();
            tools.clear();
            // Right-click also drops a half-made swap, for the same reason it drops a seed: there has to
            // be a way out of a gesture that is not "finish it".
            swapFrom = null;
            return wasHolding;
        }
        if (button != Input.Buttons.LEFT || !hovered.isValid()) {
            return false;
        }
        if (beghouledDown(hovered)) {
            return true;
        }
        return act(hovered);
    }

    // ---- Beghouled: swapping two neighbours -------------------------------------------------------

    // The tile the player has picked up, or null. Doubles as the drag origin and the tap selection,
    // which is why there is only one field: a drag and a tap-tap are the same two points, and the only
    // difference is whether the button came up on the second one or the first.
    private GridPos swapFrom;

    // What the view has selected, for the highlight. Read by GameScreen.
    public GridPos swapSelection() {
        return swapFrom;
    }

    // Picking a tile up, or completing a swap onto a neighbour.
    //
    // Both gestures the original supports fall out of one rule: a press on a NEW tile selects it, and a
    // press on a tile ADJACENT to the selection swaps. Drag-and-release is then handled by touchUp,
    // which only has to ask the same question about wherever the button came up.
    //
    // Returns false on every board that is not Beghouled, so an ordinary lawn's click path is untouched.
    private boolean beghouledDown(GridPos at) {
        if (views.gdx.render.BeghouledRenderer.modeOf(session) == null || tools.isHolding()) {
            return false;
        }
        if (swapFrom != null && isNeighbour(swapFrom, at)) {
            commands.swap(swapFrom, at);
            swapFrom = null;
            return true;
        }
        // Pressing the selected tile again puts it back down, which is the only way to change your mind
        // without making a move you did not want.
        swapFrom = at.equals(swapFrom) ? null : at;
        return true;
    }

    // Releasing the button. Only interesting as the end of a DRAG: the button went down on one tile and
    // came up on its neighbour, which is the gesture the roadmap asks for and the one a player used to
    // match-3 will reach for first.
    //
    // A release on the tile it started from is left alone deliberately -- that is a tap, and the
    // selection it made has to survive for the second tap.
    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        if (button != Input.Buttons.LEFT || swapFrom == null) {
            return false;
        }
        updateHover(screenX, screenY);
        if (!hovered.isValid() || !isNeighbour(swapFrom, hovered)) {
            return false;
        }
        commands.swap(swapFrom, hovered);
        swapFrom = null;
        return true;
    }

    // Orthogonally adjacent, which is the same test BeghouledMode.swap applies -- it refuses anything
    // else with a message, so this only stops the view offering a move the model will reject.
    private static boolean isNeighbour(GridPos a, GridPos b) {
        return Math.abs(a.col() - b.col()) + Math.abs(a.row() - b.row()) == 1;
    }

    // A click on a tile means whatever the cursor is holding.
    private boolean act(GridPos at) {
        // A sun on the clicked tile is taken first, whatever the cursor is holding. Otherwise a player
        // with a seed armed could not collect anything without disarming, and the reflex to grab a sun
        // the moment it lands would instead spend it planting.
        if (collectSunAt(at)) {
            return true;
        }
        switch (tools.tool()) {
            case SEED -> {
                // The seed is put down whether or not the planting succeeded. A refusal ("not enough
                // sun", "already a plant there") arrives as a toast, and leaving the packet armed after
                // one makes the next stray click spend sun the player did not mean to spend.
                boolean planted = commands.plant(tools.seedName(), at);
                tools.clear();
                return planted;
            }
            case NUT -> {
                // Put down whichever way it goes, for the same reason a seed is: a refusal ("stay
                // behind the red line") arrives as a toast, and a nut left armed after one makes the
                // next stray click spend a nut off the belt.
                boolean bowled = commands.bowl(tools.nutToken(), at);
                tools.clear();
                return bowled;
            }
            case ZOMBIE -> {
                boolean summoned = commands.summon(tools.zombieAlias(), at);
                tools.clear();
                return summoned;
            }
            case SHOVEL -> {
                commands.pluck(at);
                tools.clear();
                return true;
            }
            case PLANT_FOOD -> {
                commands.feed(at);
                tools.clear();
                return true;
            }
            default -> {
                // Nothing held: a bare click still collects a sun sitting on that tile, so suns can be
                // clicked as well as hovered over -- and on a Vasebreaker board it is also the only
                // gesture there is, because that mode hands out no seed packets to arm.
                return collectSunAt(at) || vasebreaker(at);
            }
        }
    }

    // What a bare click means on a Vasebreaker board: pick up a packet lying there, or smash the vase.
    //
    // The packet is tried FIRST because a vase and the seed it dropped never share a tile -- a vase
    // drops its packet onto its own cell as it breaks -- so the two are exclusive, and trying the smash
    // first would be a coin toss on the one tile where it matters.
    //
    // Only ever asked when the tile actually holds one, for the same reason a sun is: `break vase` on
    // an empty tile is refused with a message, and firing one on every stray click would flood the
    // toast overlay with "there's no vase there".
    private boolean vasebreaker(GridPos at) {
        VaseBreakerMode mode = views.gdx.render.VaseRenderer.modeOf(session);
        if (mode == null) {
            return false;
        }
        if (mode.hasDroppedSeed(at.col(), at.row())) {
            return commands.collectSeed(at);
        }
        Vase vase = mode.getVaseAt(at.col(), at.row());
        return vase != null && !vase.isBroken() && commands.breakVase(at);
    }

    // Suns are collected by CLICKING them, not by passing the cursor over them.
    //
    // Hover-collect was tried first and is wrong for this game: with a seed armed the cursor is
    // constantly crossing the board looking for a tile, and it would hoover up every sun on the way --
    // including ones the player was deliberately leaving for a Sun-shroom quest or a scoring run. The
    // click is also what the original uses.
    private void updateHover(int screenX, int screenY) {
        scratch.set(screenX, screenY, 0f);
        viewport.unproject(scratch);
        hovered = lawn.cellAt(scratch.x, scratch.y);
    }

    // Only asks the model to collect when a sun is genuinely there. Firing the command on every tile
    // the cursor crosses would work -- collectSun returns false harmlessly -- but it would also spend a
    // command, and a failed one prints "no sun there", which would flood the toast overlay.
    private boolean collectSunAt(GridPos at) {
        if (!hasSunAt(at) || !maySpendOnPlants()) {
            return false;
        }
        return commands.collectSun(at);
    }

    // The tile the collect command has to address. Asked of the sun rather than worked out here: this
    // used to reimplement SunSystem.findSunAt's rule, and the two copies read different fields on a
    // mirrored sun -- which is a sun the player can see, can click, and cannot collect.
    private static GridPos tileOf(Sun sun) {
        return new GridPos(sun.tileColumn(), sun.tileRow());
    }

    private boolean hasSunAt(GridPos at) {
        for (Sun sun : session.getActiveSuns()) {
            if (sun.isRemovable()) {
                continue;
            }
            if (tileOf(sun).equals(at)) {
                return true;
            }
        }
        return false;
    }
}
