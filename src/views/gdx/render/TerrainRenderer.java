package views.gdx.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import models.game.EnvironmentType;
import models.game.GameSession;
import models.map.Cell;
import models.map.Terrains.FrozenTerrain;
import models.map.Terrains.GraveInDarkAgesTerrain;
import models.map.Terrains.GraveTerrain;
import models.map.Terrains.LowSandTerrain;
import models.map.Terrains.NecromancyTerrain;
import models.map.Terrains.SlipDirection;
import models.map.Terrains.SlipTerrain;
import models.map.Terrains.Terrain;
import models.map.Terrains.WaterTerrain;
import views.gdx.core.Assets;
import views.gdx.map.LawnGeometry;
import views.gdx.sprite.ClipMap;
import views.gdx.sprite.EntitySprite;
import views.gdx.sprite.SpriteRegistry;

// Draws whatever is sitting ON a tile rather than planted in it: headstones, ice blocks, the slider
// tiles of Frostbite Caves, the beach's water, and the cursed ground of the Dark Ages.
//
// Graves were the urgent case. Ancient Egypt's very first level places them, the terminal build shows
// them as '#', and until this class existed the GUI drew nothing at all -- so a tile you could not
// plant on looked completely ordinary. Every terrain below has the same problem: the model has enforced
// all of them since Phase 1, and until now every one of them was invisible.
//
// The art carries damage states as clips (undamaged, damage1..damage4), and GraveTerrain already
// tracks hp against a max, so a headstone visibly crumbles as it is shot for free. That is the
// "tomb degradation" item from the spec's aesthetics list.
public final class TerrainRenderer {

    private static final String[] DAMAGE_CLIPS = {
            "undamaged", "damage1", "damage2", "damage3", "damage4"
    };

    // Frostbite. The ice block ships in two halves so whatever is frozen sits INSIDE it rather than
    // behind a sticker, and in two flavours because a caged plant and a caged zombie are different
    // shapes. FrozenTerrain already knows which it is holding.
    private static final String ICE_PLANT_FRONT = "FROSTBITE_ICE_BLOCK_PLANT";
    private static final String ICE_PLANT_BEHIND = "FROSTBITE_ICE_BLOCK_PLANT_BEHIND";
    private static final String ICE_ZOMBIE_FRONT = "FROSTBITE_ICE_BLOCK_ZOMBIE";
    private static final String ICE_ZOMBIE_BEHIND = "FROSTBITE_ICE_BLOCK_ZOMBIE_BEHIND";

    // Only the PLANT block ships freeze_start/freeze_idle; the zombie block has an `idle` and nothing
    // else. Both are offered so each sprite takes the best clip it actually has, rather than relying on
    // firstAvailable's blind fall-through.
    private static final String[] ICE_FRONT_CLIPS = {"freeze_idle", "idle"};
    private static final String[] REAR_CLIPS = {"idle"};
    private static final String SLIDER_UP = "TILESLIDER_ICEAGE_UP";
    private static final String SLIDER_DOWN = "TILESLIDER_ICEAGE_DOWN";

    // Big Wave Beach. WATER_SQUARE is one flooded tile, animated; WATER_TIDE_LINE is the foam edge that
    // marks where the water currently stops.
    private static final String WATER_TILE = "WATER_SQUARE";
    private static final String WATER_TILE_CLIP = "Water";
    private static final String TIDE_LINE = "WATER_TIDE_LINE";

    // Ground that is marked rather than occupied. Neither ships art of its own -- a low sand bank and a
    // cursed patch are both simply GROUND that behaves differently -- so they are washes, the same way
    // the placement highlight and the grid are.
    private static final Color LOW_SAND = new Color(0.42f, 0.34f, 0.18f, 0.42f);
    private static final Color NECROMANCY = new Color(0.45f, 0.16f, 0.62f, 0.34f);
    private static final Color NECROMANCY_PULSE = new Color(0.72f, 0.35f, 0.95f, 0.22f);
    private static final float PULSE_HZ = 0.4f;

    // How much of a tile each piece covers. Note there is no ICE_WIDTH_CELLS: an ice block is not a tile
    // decal and is not scaled to one -- see drawIce.
    private static final float SLIDER_WIDTH_CELLS = 0.95f;
    private static final float TIDE_LINE_WIDTH_CELLS = 0.55f;

    // How much of whatever is frozen still reads through the front half of the block.
    //
    // Was 0.68, which is transparent enough on paper and opaque in practice: Frostbite Caves is painted
    // in near-white ice, the block art is near-white too, and 32% of a zombie behind 68% of a white
    // lozenge on a white floor is not a zombie anybody can see. Judged against THIS world's background
    // rather than against a neutral one.
    private static final float ICE_FRONT_ALPHA = 0.45f;

    // Where a tile decal sits vertically, as a fraction of cell height from the bottom.
    //
    // 0.5 -- the geometric centre of the cell, which is where the background art's painted tile is. This
    // was 0.40 on the theory that a decal should sit low to match the perspective a plant stands in at
    // FOOT_INSET. That reasoning belongs to things that STAND on the ground, not to things that ARE the
    // ground: it left every slider tile, water tile and gold tile about a tenth of a cell below the
    // painted square it was supposed to be covering. Anything that should sit on the foot line instead
    // asks for the foot line directly.
    private static final float DECAL_CENTRE = 0.5f;

    private final SpriteRegistry sprites;
    private final LawnGeometry lawn;
    private final EnvironmentType environment;
    private final AnimationClocks clocks;

    // Headstones flash white when hit, exactly as plants and zombies do. Swept from GameRenderer's
    // per-frame sweep alongside the other two.
    private final DamageFlash flashes = new DamageFlash();

    void sweepFlashes() {
        flashes.sweep();
    }
    private final Assets assets;

    private float clock;

    public TerrainRenderer(Assets assets, SpriteRegistry sprites, LawnGeometry lawn,
                           AnimationClocks clocks, EnvironmentType environment) {
        this.assets = assets;
        this.sprites = sprites;
        this.lawn = lawn;
        this.clocks = clocks;
        this.environment = environment;
    }

    // The game's own gold tile, for Save Our Seeds' defended plants.
    //
    // Replaces a gold rim drawn with the ShapeRenderer. That was legible but invented, and the dump has
    // the real thing: GOLDTILE is a full tile animation with an `active_idle` shimmer, which is exactly
    // what "this square is special, protect it" looks like in the game the art came from.
    private static final String GOLD_TILE = "GOLDTILE";
    private static final String GOLD_TILE_CLIP = "active_idle";

    // Drawn once per lane, under the plants standing on it -- a tile the plant is on, not a marker over
    // it. Read off the mode's live list, which IS the lose condition: checkLose watches these exact
    // objects, so a plant that stopped being watched stops being gilded.
    public void drawGuardedTiles(Batch batch, GameSession session, int row, float delta) {
        if (session == null
                || !(session.getMode() instanceof models.game.gamemodes.SaveOurSeedsMode guard)) {
            return;
        }
        EntitySprite sprite = sprites.get(GOLD_TILE);
        String clip = ClipMap.firstAvailable(sprite, GOLD_TILE_CLIP);
        for (models.entities.plants.Plant plant : guard.getProtectedPlants()) {
            if (plant == null || plant.isDead() || plant.getY() != row) {
                continue;
            }
            int col = (int) plant.getX();
            float stateTime = ClipMap.sample(sprite, clip,
                    clocks.advance(GOLD_TILE + col + "," + row, clip, delta));
            covering(batch, sprite, clip, stateTime, col, row);
        }
    }

    // Called once per lane, before its cells. The waterline is a property of the MAP rather than of any
    // tile -- the tide moves it every wave -- so it cannot come out of the per-cell pass.
    public void drawWaterline(Batch batch, GameSession session, int row, float delta) {
        clock += delta / Math.max(1, utils.Constants.BOARD_ROWS);   // advanced once per frame, not per lane
        if (session == null || !session.getMap().hasTide()) {
            return;
        }
        int edge = leftmostFlooded(session, row);
        if (edge < 0) {
            return;
        }
        EntitySprite sprite = sprites.get(TIDE_LINE);
        String clip = ClipMap.firstAvailable(sprite, "idle");
        // On the tile boundary, not the tile centre: the line IS the edge of the water, and half a cell
        // off is half a cell of sand drawn as sea.
        fitted(batch, sprite, clip, 0f, lawn.worldX(edge), tileCentreY(row), TIDE_LINE_WIDTH_CELLS);
    }

    public void drawCell(Batch batch, Cell cell, int col, int row, float delta) {
        // Water first and separately: it is read off the CELL's flooded flag, which the tide sets, not
        // off a terrain marker -- a tile can be flooded with nothing else on it at all.
        if (cell.isFlooded()) {
            drawWater(batch, col, row, delta);
        }
        if (cell.getTerrain() == null || cell.getTerrain().isEmpty()) {
            return;
        }
        for (Terrain terrain : cell.getTerrain()) {
            if (terrain.isDestroyed()) {
                continue;   // removed from the cell at the end of the tick; do not draw a broken stone
            }
            drawTerrain(batch, terrain, col, row, delta);
        }
    }

    private void drawTerrain(Batch batch, Terrain terrain, int col, int row, float delta) {
        if (terrain instanceof GraveTerrain grave) {
            drawGrave(batch, grave, col, row, delta);
        } else if (terrain instanceof SlipTerrain slip) {
            drawSlider(batch, slip, col, row, delta);
        } else if (terrain instanceof FrozenTerrain ice) {
            drawIce(batch, behindSpriteFor(ice), REAR_CLIPS, col, row, delta, 1f);
        } else if (terrain instanceof NecromancyTerrain) {
            wash(batch, col, row, NECROMANCY);
            wash(batch, col, row, pulsed(NECROMANCY_PULSE));
        } else if (terrain instanceof LowSandTerrain) {
            wash(batch, col, row, LOW_SAND);
        } else if (terrain instanceof WaterTerrain) {
            drawWater(batch, col, row, delta);   // a marker on a cell the tide has not flagged
        }
    }

    // The front half of an ice block, drawn AFTER the plants and zombies of the lane so whatever is
    // frozen is inside the ice rather than behind it. Two passes over the same terrain list is the
    // price of that, and it is the only way the shipped two-part art can be used as intended.
    public void drawCellFront(Batch batch, Cell cell, int col, int row, float delta) {
        if (cell.getTerrain() == null || cell.getTerrain().isEmpty()) {
            return;
        }
        for (Terrain terrain : cell.getTerrain()) {
            if (!terrain.isDestroyed() && terrain instanceof FrozenTerrain ice) {
                drawIce(batch, frontSpriteFor(ice), ICE_FRONT_CLIPS, col, row, delta, ICE_FRONT_ALPHA);
            }
        }
    }

    // ---- the pieces ------------------------------------------------------------------------------

    private void drawGrave(Batch batch, GraveTerrain grave, int col, int row, float delta) {
        EntitySprite sprite = sprites.get(graveSpriteName(grave));
        String clip = ClipMap.firstAvailable(sprite, damageClipFor(grave));
        float stateTime = ClipMap.sample(sprite, clip, clocks.advance(grave, clip, delta));
        float centreX = lawn.centerX(col);
        float footY = lawn.worldY(row) + lawn.cellHeight() * SpritePlacer.FOOT_INSET;

        SpritePlacer.drawStanding(batch, sprite, clip, stateTime, centreX, footY, true, null);

        // A headstone takes fire like anything else, so it flashes like anything else (T8.4).
        //
        // DamageFlash is keyed on Object and only ever asked for a number, so it needed no widening to
        // cover a Terrain -- and it fills a real gap: a grave's five damage clips are the only feedback
        // it has, and they are five steps across thousands of HP, so most shots into a headstone
        // currently register as nothing at all.
        float flash = flashes.intensity(grave, grave.getHp(), delta);
        if (flash > 0f) {
            float previous = batch.getPackedColor();
            SpritePlacer.beginAdditive(batch);
            batch.setColor(flash, flash, flash, 1f);
            SpritePlacer.drawStanding(batch, sprite, clip, stateTime, centreX, footY, true, null);
            SpritePlacer.endAdditive(batch);
            batch.setPackedColor(previous);
        }
    }

    // Which way the tile shoves a zombie is the whole rule, so the two directions get the two
    // animations the game ships rather than one arrow drawn twice.
    private void drawSlider(Batch batch, SlipTerrain slip, int col, int row, float delta) {
        EntitySprite sprite = sprites.get(
                slip.getDirection() == SlipDirection.UP ? SLIDER_UP : SLIDER_DOWN);
        String clip = ClipMap.firstAvailable(sprite, "idle");
        float stateTime = ClipMap.sample(sprite, clip, clocks.advance(slip, clip, delta));
        fitted(batch, sprite, clip, stateTime, lawn.centerX(col), tileCentreY(row),
                SLIDER_WIDTH_CELLS);
    }

    // An ice block STANDS on the lane's foot line at its own authored size. It is not a tile decal, and
    // treating it as one was the whole bug.
    //
    // These blocks are authored at the same 768 resolution as the characters they encase -- the zombie
    // block measures 153x243 against a walking zombie's ~250, the plant block 164x171 against a
    // Peashooter's ~149 -- so at the shared SPRITE_SCALE they already wrap their occupant exactly, and
    // the correct amount of fitting to do is NONE. Squeezing the zombie block to one cell WIDTH
    // (82 world px) shrank it to 82x130 and then centred that on a tile-decal point 0.40 up the cell,
    // which put a small block across a 160px zombie's shins with its head and torso standing out in the
    // open. Standing it on `worldY(row) + cellHeight * FOOT_INSET` -- the exact line PlantRenderer and
    // ZombieRenderer put their feet on -- is what makes the occupant end up INSIDE the ice.
    //
    // It also aligns the two halves with each other: the front is authored taller than the rear, so
    // centring both on one point pushed the front's extra height out of the bottom of the block as well
    // as the top. Standing them puts both on the same ground.
    //
    // alpha < 1 for the front half, so whatever is frozen reads THROUGH the ice rather than being buried
    // by it. Multiplied INTO the batch's current tint and restored by packed value rather than reset to
    // Color.WHITE: the lane pass can already be tinted (Night Ops dims the whole board), and forcing
    // white here would silently undo it for everything drawn afterwards.
    private void drawIce(Batch batch, String spriteName, String[] preferredClips,
                         int col, int row, float delta, float alpha) {
        EntitySprite sprite = sprites.get(spriteName);
        if (sprite == null || !sprite.isReady()) {
            return;
        }
        String clip = ClipMap.firstAvailable(sprite, preferredClips);
        float stateTime = ClipMap.sample(sprite, clip, clocks.advance(spriteName + col + "," + row,
                clip, delta));

        float previous = batch.getPackedColor();
        Color tint = batch.getColor();
        batch.setColor(tint.r, tint.g, tint.b, tint.a * alpha);
        SpritePlacer.drawStanding(batch, sprite, clip, stateTime,
                lawn.centerX(col),
                lawn.worldY(row) + lawn.cellHeight() * SpritePlacer.FOOT_INSET,
                true, null);
        batch.setPackedColor(previous);
    }

    // How fast the sea moves, and how far each tile lags the one behind it.
    //
    // The clip runs 2.6s at its authored rate, which for a wide calm sea is a fidget rather than a
    // swell -- slowed to a third, it breathes. The per-tile lag is what turns it from a sea that
    // pulses as ONE surface into one where the swell travels across the board: a single shared clock
    // was right for keeping the tiles in phase, and wrong about them being in the SAME phase.
    private static final float WATER_SPEED = 0.34f;
    private static final float WATER_LAG_PER_TILE = 0.14f;

    private void drawWater(Batch batch, int col, int row, float delta) {
        EntitySprite sprite = sprites.get(WATER_TILE);
        String clip = ClipMap.firstAvailable(sprite, WATER_TILE_CLIP);
        // The renderer's own frame clock, not an AnimationClocks entry: this is called once per FLOODED
        // TILE, so advancing a clock here would run the sea ten times too fast on a wide beach and at a
        // different speed as the tide came in.
        float elapsed = clock * WATER_SPEED + (col + row) * WATER_LAG_PER_TILE;
        float cycle = sprite.clipDuration(clip);
        if (cycle > 0f) {
            elapsed = elapsed % cycle;   // wrapped, or a lagged tile clamps on its last frame
        }
        covering(batch, sprite, clip, ClipMap.sample(sprite, clip, elapsed), col, row);
    }

    // ---- drawing helpers -------------------------------------------------------------------------

    // Scales a sprite to a given number of cells wide and centres it on a point.
    //
    // Needed because these are authored on a 390x390 canvas -- a WATER_SQUARE drawn at its own size
    // covers two and a half lawn tiles. SpritePlacer stands things at their authored size, which is
    // right for characters and wrong for a tile decal.
    private void fitted(Batch batch, EntitySprite sprite, String clip, float stateTime,
                        float centreX, float centreY, float widthCells) {
        Rectangle bounds = boundsOf(sprite, clip);
        if (bounds == null) {
            return;
        }
        float scale = SpritePlacer.toSpriteSpace(widthCells * lawn.cellWidth()) / bounds.width;
        drawScaled(batch, sprite, clip, stateTime, centreX, centreY, scale, bounds);
    }

    // Scales a sprite to COVER a whole cell rather than to fit inside it.
    //
    // A lawn cell is 82x97 -- taller than it is wide -- and the water square is authored square, so
    // fitting it to the cell's width left a strip of dry sand between every pair of flooded tiles.
    // The sea has to tile seamlessly or it is not a sea.
    private void covering(Batch batch, EntitySprite sprite, String clip, float stateTime,
                          int col, int row) {
        Rectangle bounds = boundsOf(sprite, clip);
        if (bounds == null || bounds.height <= 0f) {
            return;
        }
        float scale = Math.max(
                SpritePlacer.toSpriteSpace(lawn.cellWidth()) / bounds.width,
                SpritePlacer.toSpriteSpace(lawn.cellHeight()) / bounds.height);
        // A hair over, so two neighbours meet with no hairline of background between them.
        drawScaled(batch, sprite, clip, stateTime, lawn.centerX(col), tileCentreY(row),
                scale * 1.03f, bounds);
    }

    private Rectangle boundsOf(EntitySprite sprite, String clip) {
        if (sprite == null || !sprite.isReady()) {
            return null;
        }
        Rectangle bounds = sprite.bounds(clip);
        return bounds == null || bounds.width <= 0f ? null : bounds;
    }

    private void drawScaled(Batch batch, EntitySprite sprite, String clip, float stateTime,
                            float centreX, float centreY, float scale, Rectangle bounds) {
        Matrix4 previous = batch.getTransformMatrix().cpy();
        batch.setTransformMatrix(new Matrix4(previous)
                .translate(SpritePlacer.toSpriteSpace(centreX), SpritePlacer.toSpriteSpace(centreY), 0f)
                .scale(scale, scale, 1f));
        // Same y-down correction as everywhere else: the art hangs below the .PAM origin.
        sprite.draw(batch, clip, stateTime, 0f, bounds.y + bounds.height / 2f, true);
        batch.setTransformMatrix(previous);
    }

    // A flat tint over one tile, for ground that is marked rather than occupied.
    private void wash(Batch batch, int col, int row, Color color) {
        float previous = batch.getPackedColor();
        batch.setColor(Color.WHITE);
        assets.solid(color).draw(batch,
                SpritePlacer.toSpriteSpace(lawn.worldX(col)),
                SpritePlacer.toSpriteSpace(lawn.worldY(row)),
                SpritePlacer.toSpriteSpace(lawn.cellWidth()),
                SpritePlacer.toSpriteSpace(lawn.cellHeight()));
        batch.setPackedColor(previous);
    }

    private Color pulsed(Color base) {
        float wave = (float) (0.5d + 0.5d * Math.sin(clock * PULSE_HZ * Math.PI * 2d));
        return new Color(base.r, base.g, base.b, base.a * wave);
    }

    private float tileCentreY(int row) {
        return lawn.worldY(row) + lawn.cellHeight() * DECAL_CENTRE;
    }

    // The first flooded column in a lane -- where the water's edge currently is.
    private static int leftmostFlooded(GameSession session, int row) {
        for (int col = 0; col < utils.Constants.BOARD_COLS; col++) {
            if (session.getMap().getRow(row).cellAt(col).isFlooded()) {
                return col;
            }
        }
        return -1;
    }

    // A plain authored '&' obstacle holds nothing and reports a null type; it gets the plant block,
    // which is the tile-sized one. The zombie block is taller, for something standing up inside it.
    private static boolean holdsZombie(FrozenTerrain ice) {
        return "zombie".equalsIgnoreCase(ice.getInnerType());
    }

    private static String frontSpriteFor(FrozenTerrain ice) {
        return holdsZombie(ice) ? ICE_ZOMBIE_FRONT : ICE_PLANT_FRONT;
    }

    private static String behindSpriteFor(FrozenTerrain ice) {
        return holdsZombie(ice) ? ICE_ZOMBIE_BEHIND : ICE_PLANT_BEHIND;
    }

    // Dark Ages headstones come in three flavours and the spec explicitly asks that they look
    // different; the art happens to ship exactly those three. Everywhere else the world decides.
    private String graveSpriteName(GraveTerrain grave) {
        if (grave instanceof GraveInDarkAgesTerrain dark) {
            return switch (dark.getType()) {
                case SUNNY -> "DARK_SUN";
                case FOODY -> "DARK_PLANTFOOD";
                case PLAIN -> "DARK_NOOP";
            };
        }
        if (environment == null) {
            return "TUTORIAL_GRAVESTONE";
        }
        return switch (environment) {
            case ANCIENT_EGYPT -> "EGYPT_HIEROGLYPH";
            case DARK_AGES -> "DARK_NOOP";
            // Frostbite and the beach have no headstones of their own in this dump; the plain
            // gravestone reads correctly on any ground.
            case FROSTBITE_CAVES, BIG_WAVE_BEACH -> "TUTORIAL_GRAVESTONE";
        };
    }

    // Five art states across the headstone's health, so it crumbles as it is shot.
    private static String damageClipFor(GraveTerrain grave) {
        int max = Math.max(1, grave.getMaxHp());
        float remaining = grave.getHp() / (float) max;
        // The LAST clip is the headstone already gone -- rubble, or nothing at all. Spreading the
        // health range across every clip therefore made a nearly-dead grave render as empty ground
        // while it was still standing and still blocking shots. A grave that is alive is always drawn
        // in a state you can see; only isDestroyed() removes it.
        int usable = DAMAGE_CLIPS.length - 1;          // exclude the vanished pose
        int index = Math.round((1f - remaining) * (usable - 1));
        return DAMAGE_CLIPS[Math.max(0, Math.min(usable - 1, index))];
    }
}
