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
            Sun airborne = collectibles.sunAt(scratch.x, scratch.y);
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
            return wasHolding;
        }
        if (button != Input.Buttons.LEFT || !hovered.isValid()) {
            return false;
        }
        return act(hovered);
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
        if (!hasSunAt(at)) {
            return false;
        }
        return commands.collectSun(at);
    }

    // The tile the model files this sun under -- which is what the collect command addresses, even
    // while the sun is drawn high above it. A falling sun is claimed by the tile it is heading FOR,
    // matching how SunSystem.findSunAt locates one.
    private static GridPos tileOf(Sun sun) {
        int row = sun.isFalling() ? (int) Math.floor(sun.getTargetY()) : sun.getY();
        return new GridPos((int) Math.floor(sun.getX()), row);
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
