package views.gdx.render;

import com.badlogic.gdx.graphics.g2d.Batch;
import models.entities.projectiles.Projectile;
import models.entities.zombies.Zombie;
import models.game.EnvironmentType;
import models.game.GameSession;
import models.map.Row;
import utils.Constants;
import views.gdx.bridge.EntityInterpolator;
import views.gdx.core.Assets;
import views.gdx.map.LawnGeometry;
import views.gdx.sprite.SpriteRegistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// Draws everything on the lawn, in the order it has to be drawn.
//
// Z-order is not a separate sorting pass here; it falls out of the iteration order, which is both
// cheaper and harder to get wrong:
//
//   for each lane, TOP (row 0) to BOTTOM (row 4):
//       lawnmower, plants, zombies, projectiles
//
// Because row 4 is nearest the viewer, drawing lanes in ascending order means a zombie in a lower row
// naturally covers one in a higher row -- the "entities lower on the screen render on top" rule from
// the spec's aesthetics list, for free. Within a lane, zombies are drawn right-to-left so the one
// closest to the house ends up in front.
public final class GameRenderer {

    private final PlantRenderer plants;
    private final ZombieRenderer zombies;
    private final ProjectileRenderer projectiles;
    private final CollectibleRenderer collectibles;
    private final LawnmowerRenderer mowers;

    // Reused per lane so a full board does not allocate a list per row per frame.
    private final List<Zombie> laneZombies = new ArrayList<>();

    // Furthest-from-the-house first, so the leading zombie is drawn last and therefore on top.
    private static final Comparator<Zombie> BACK_TO_FRONT =
            Comparator.comparingDouble(z -> -z.getMovement().getPositionX());

    public GameRenderer(Assets assets, SpriteRegistry sprites, LawnGeometry lawn,
                        EntityInterpolator interpolator, EnvironmentType environment) {
        this.plants = new PlantRenderer(sprites, lawn);
        this.zombies = new ZombieRenderer(sprites, lawn, interpolator);
        this.projectiles = new ProjectileRenderer(assets, lawn, interpolator);
        this.collectibles = new CollectibleRenderer(sprites, lawn, interpolator);
        this.mowers = new LawnmowerRenderer(sprites, lawn, interpolator, environment);
    }

    public void draw(Batch batch, GameSession session, float stateTime, float alpha) {
        // One transform for the whole pass -- see SpritePlacer.SPRITE_SCALE. Setting it per entity
        // would flush the batch once per sprite.
        com.badlogic.gdx.math.Matrix4 previousTransform = SpritePlacer.beginScaled(batch);
        try {
            drawLanes(batch, session, stateTime, alpha);
        } finally {
            SpritePlacer.endScaled(batch, previousTransform);
        }
    }

    private void drawLanes(Batch batch, GameSession session, float stateTime, float alpha) {
        for (int row = 0; row < Constants.BOARD_ROWS; row++) {
            Row lane = session.getMap().getRow(row);

            mowers.draw(batch, lane.getLawnmower(), row, stateTime, alpha);

            for (int col = 0; col < Constants.BOARD_COLS; col++) {
                plants.drawCell(batch, lane.cellAt(col), col, row, stateTime);
            }

            laneZombies.clear();
            laneZombies.addAll(lane.getZombies());
            laneZombies.sort(BACK_TO_FRONT);
            for (Zombie zombie : laneZombies) {
                zombies.draw(batch, zombie, stateTime, alpha);
            }

            // Shots last within the lane: a pea should pass in front of the plant that fired it and
            // the zombie it is about to hit, not disappear behind them.
            for (Projectile projectile : new ArrayList<>(lane.getActiveProjectiles())) {
                projectiles.draw(batch, projectile, alpha);
            }
        }

        // Suns float above the whole board and are collected by hovering, so they are drawn over
        // everything rather than per lane.
        collectibles.draw(batch, session, stateTime, alpha);
    }
}
