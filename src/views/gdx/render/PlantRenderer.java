package views.gdx.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import models.entities.plants.Plant;
import models.map.Cell;
import views.gdx.map.LawnGeometry;
import views.gdx.sprite.ClipMap;
import views.gdx.sprite.EntitySprite;
import views.gdx.sprite.SpriteRegistry;

// Draws the plants of one lane.
//
// A tile can hold three plants at once and the order they are drawn in is not cosmetic -- it is the
// same order zombies eat through them (Cell.getDefendingPlant): a Lily Pad is the platform underneath,
// the real plant sits on it, and a Pumpkin is the shell in front. Drawing them in stack order is what
// makes that readable.
public final class PlantRenderer {

    // Frozen plants are encased in ice. Phase 1 tints rather than drawing an ice block; the block
    // itself is Frostbite Caves work (T7.7). Three steps, matching Plant.getChillLevel()'s 1..3.
    private static final Color[] CHILL_TINT = {
            Color.WHITE,
            new Color(0.80f, 0.92f, 1f, 1f),
            new Color(0.62f, 0.84f, 1f, 1f),
            new Color(0.45f, 0.76f, 1f, 1f),
    };

    // A plant with an octopus on it is being smothered and cannot act.
    private static final Color OCTOPUS_TINT = new Color(0.85f, 0.6f, 0.85f, 1f);

    private final SpriteRegistry sprites;
    private final LawnGeometry lawn;

    public PlantRenderer(SpriteRegistry sprites, LawnGeometry lawn) {
        this.sprites = sprites;
        this.lawn = lawn;
    }

    public void drawCell(Batch batch, Cell cell, int col, int row, float stateTime) {
        // Bottom of the stack first. A dead-but-not-yet-swept plant is skipped: it stays in its cell
        // until the end of the tick it died on, and drawing it would show a corpse standing.
        draw(batch, cell.getPlatform(), col, row, stateTime);
        draw(batch, cell.getCurrentPlant(), col, row, stateTime);
        draw(batch, cell.getProtector(), col, row, stateTime);
    }

    private void draw(Batch batch, Plant plant, int col, int row, float stateTime) {
        if (plant == null || plant.isDead()) {
            return;
        }
        EntitySprite sprite = sprites.get(plant.getName());
        String clip = ClipMap.firstAvailable(sprite, ClipMap.IDLE);

        Color previous = batch.getColor().cpy();
        batch.setColor(tintFor(plant));

        // Plants face right, toward the oncoming horde.
        SpritePlacer.drawStanding(batch, sprite, clip, stateTime,
                lawn.centerX(col), footY(row), true, null);

        batch.setColor(previous);
    }

    private float footY(int row) {
        return lawn.worldY(row) + lawn.cellHeight() * SpritePlacer.FOOT_INSET;
    }

    private static Color tintFor(Plant plant) {
        if (plant.hasOctopus()) {
            return OCTOPUS_TINT;
        }
        int chill = plant.getChillLevel();
        if (chill > 0) {
            return CHILL_TINT[Math.min(chill, CHILL_TINT.length - 1)];
        }
        return Color.WHITE;
    }
}
