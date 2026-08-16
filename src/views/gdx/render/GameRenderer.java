package views.gdx.render;

import com.badlogic.gdx.graphics.g2d.Batch;
import models.entities.projectiles.Projectile;
import models.entities.zombies.Zombie;
import models.game.EnvironmentType;
import models.game.GameSession;
import models.map.Cell;
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

    private final TerrainRenderer terrain;
    private final PlantRenderer plants;
    private final ZombieRenderer zombies;
    private final ProjectileRenderer projectiles;
    private final CollectibleRenderer collectibles;
    private final LawnmowerRenderer mowers;
    private final ImpactEffects impacts;
    private final ExplosionEffects explosions;

    // Reused per lane so a full board does not allocate a list per row per frame.
    private final List<Zombie> laneZombies = new ArrayList<>();

    // How close a shot has to be to its shooter for the shooter to be redrawn over it.
    private static final double MUZZLE_OVERLAP_CELLS = 0.75;

    // Per-entity animation clocks, so the horde does not step in unison and clip changes restart.
    private final AnimationClocks clocks = new AnimationClocks();
    private LawnGeometry lawn;

    // Projectiles already seen, so a shot that vanishes can be spotted and turned into an impact.
    //
    // This used to double as the trigger for the shooter's attack animation, on the reasoning that a
    // new projectile is the only sign the view gets that a plant fired. It is not needed for that any
    // more: the model announces its wind-up, so PlantRenderer starts the animation BEFORE the shot
    // instead of after it.
    private final java.util.Set<Projectile> knownShots =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    private final java.util.Set<Projectile> liveShots =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    // Furthest-from-the-house first, so the leading zombie is drawn last and therefore on top.
    private static final Comparator<Zombie> BACK_TO_FRONT =
            Comparator.comparingDouble(z -> -z.getMovement().getPositionX());

    public GameRenderer(Assets assets, SpriteRegistry sprites, LawnGeometry lawn,
                        EntityInterpolator interpolator, EnvironmentType environment) {
        this.terrain = new TerrainRenderer(sprites, lawn, clocks, environment);
        this.plants = new PlantRenderer(sprites, lawn, clocks);
        this.zombies = new ZombieRenderer(sprites, lawn, interpolator, clocks);
        this.projectiles = new ProjectileRenderer(assets, lawn, interpolator, sprites);
        this.collectibles = new CollectibleRenderer(sprites, lawn, interpolator, clocks);
        this.mowers = new LawnmowerRenderer(sprites, lawn, interpolator, clocks, environment);
        this.impacts = new ImpactEffects(sprites);
        this.explosions = new ExplosionEffects(sprites, lawn);
        this.explosions.setCollectibles(this.collectibles);
        this.lawn = lawn;
    }

    // Input needs to hit-test suns against where they were DRAWN, not where the model files them, so
    // that a falling sun can be clicked in mid-air. Only the renderer knows the drawn position.
    public CollectibleRenderer collectibles() {
        return collectibles;
    }

    // Diagnostic: what clip an entity is playing and how far into it.
    public String describeClock(Object key) {
        return clocks.describe(key);
    }

    public void draw(Batch batch, GameSession session, float delta, float alpha) {
        // One transform for the whole pass -- see SpritePlacer.SPRITE_SCALE. Setting it per entity
        // would flush the batch once per sprite.
        com.badlogic.gdx.math.Matrix4 previousTransform = SpritePlacer.beginScaled(batch);
        try {
            drawLanes(batch, session, delta, alpha);
        } finally {
            SpritePlacer.endScaled(batch, previousTransform);
        }
        clocks.sweep();
        plants.sweepFlashes();
        zombies.sweepFlashes();
    }

    // The model narrates detonations; this is where the view hears about them.
    public ExplosionEffects explosions() {
        return explosions;
    }

    private void drawLanes(Batch batch, GameSession session, float delta, float alpha) {
        // The blast's rear half, under everything it engulfs.
        explosions.drawRear(batch, delta);

        for (int row = 0; row < Constants.BOARD_ROWS; row++) {
            Row lane = session.getMap().getRow(row);

            mowers.draw(batch, lane.getLawnmower(), row, delta, alpha);

            // Terrain before plants: a Grave Buster is planted ON a headstone and has to sit in front
            // of it, and ice blocks likewise wrap what is underneath.
            for (int col = 0; col < Constants.BOARD_COLS; col++) {
                Cell cell = lane.cellAt(col);
                terrain.drawCell(batch, cell, col, row, delta);
            }

            // Order within a lane: plants, then zombies, then shots.
            //
            // A zombie stands IN FRONT of the plant it is eating, so zombies draw over plants. Shots
            // go last so a pea stays visible as it passes a zombie -- drawing them earlier hid every
            // shot behind the target it was about to hit.
            //
            // This does mean a shot is no longer strictly "under" its shooter. That request is still
            // satisfied in practice by ProjectileRenderer.muzzleAdjusted, which anchors the start of
            // the flight to the plant's mouth: the pea emerges from the barrel rather than from the
            // plant's face, which was the actual goal. The two cannot both hold literally once zombies
            // sit above plants.
            for (int col = 0; col < Constants.BOARD_COLS; col++) {
                plants.drawCell(batch, lane.cellAt(col), col, row, delta);
            }

            laneZombies.clear();
            laneZombies.addAll(lane.getZombies());
            laneZombies.sort(BACK_TO_FRONT);
            for (Zombie zombie : laneZombies) {
                zombies.draw(batch, zombie, delta, alpha);
            }

            for (Projectile projectile : new ArrayList<>(lane.getActiveProjectiles())) {
                knownShots.add(projectile);
                projectiles.draw(batch, projectile, alpha, delta, laneZombies);
            }

            // "Under its shooter, over everything else" cannot come from layer order alone -- it is a
            // per-entity relationship. So shots are drawn above all plants, and then any shooter with a
            // pea still leaving its barrel is stamped back on top. Only that one plant is redrawn, and
            // only while the shot overlaps it, so the pea emerges from behind the barrel and is in
            // front of every other plant and zombie from then on.
            for (Projectile projectile : lane.getActiveProjectiles()) {
                models.entities.plants.Plant shooter = projectile.getShooter();
                if (shooter != null
                        && Math.abs(projectile.getX() - shooter.getX()) < MUZZLE_OVERLAP_CELLS) {
                    plants.redraw(batch, shooter);
                }
            }
        }

        // Spent shots must not accumulate for the whole level: keep only what is still in flight.
        liveShots.clear();
        for (Row lane : session.getMap().getRows()) {
            liveShots.addAll(lane.getActiveProjectiles());
        }
        // A shot that was in flight and is now gone has landed -- on a zombie, a grave, or the edge.
        // The model records no impact event, so its disappearance IS the event.
        for (Projectile spent : knownShots) {
            if (!liveShots.contains(spent)) {
                impacts.spawn(projectiles.lastDrawnX(spent), projectiles.lastDrawnY(spent),
                        projectiles.tintOf(spent), spent.getElement(), spent.getType());
            }
        }
        knownShots.retainAll(liveShots);
        projectiles.forgetAllExcept(liveShots);
        collectEffects();
        impacts.draw(batch, delta, lawn.cellWidth());

        // Suns float above the whole board and are collected by hovering, so they are drawn over
        // everything rather than per lane.
        collectibles.draw(batch, session, delta, alpha);

        // The blast's front half, over everything -- drawn after the suns so a detonation is not hidden
        // behind one that happens to be sitting on the same tile.
        explosions.drawFront(batch);
    }

    // Effects raised during the lane pass, handed to ImpactEffects once it is over.
    //
    // Not drawn where they were raised: the lane pass runs bottom lane to top, so anything drawn inside
    // it is covered by the next lane's plants. Muzzle flashes come from the projectile renderer (the
    // frame a shot appears is the frame its plant fired); strikes come from the plant renderer, for the
    // three plants that hit something with nothing in flight at all.
    private void collectEffects() {
        for (ProjectileRenderer.Muzzle muzzle : projectiles.drainMuzzles()) {
            impacts.spawnMuzzle(muzzle.x(), muzzle.y(), muzzle.sprite());
        }
        for (PlantRenderer.Strike strike : plants.drainStrikes()) {
            impacts.spawnStrike(strike.fromX(), strike.fromY(), strike.toX(), strike.toY(),
                    strike.sprite());
        }
    }
}
