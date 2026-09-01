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
    private final VaseRenderer vases;
    private final BowlingRenderer nuts;
    private final IZombieRenderer brains;
    private final WeatherEffects weather;
    private final ImpactEffects impacts;
    private final ExplosionEffects explosions;
    // A Zomboss's attacks. Silent on every board without one.
    private final BossEffects bossEffects;
    private final DeathEffects deaths;
    // Craters and the settle flash after a match. Idle on every board that is not Beghouled.
    private final BeghouledRenderer beghouled;

    // Reused per lane so a full board does not allocate a list per row per frame.
    private final List<Zombie> laneZombies = new ArrayList<>();

    // Set by GameScreen. Null in any harness that builds a renderer without a game around it, and the
    // one cue raised from in here is guarded accordingly.
    private views.gdx.core.AudioManager audio;

    public void setAudio(views.gdx.core.AudioManager audio) {
        this.audio = audio;
        // The head pop is raised from inside Dismemberment, where the throw happens.
        zombies.pieces().setAudio(audio);
    }

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
        this.terrain = new TerrainRenderer(assets, sprites, lawn, clocks, environment);
        this.plants = new PlantRenderer(sprites, lawn, clocks);
        this.zombies = new ZombieRenderer(sprites, lawn, interpolator, clocks, environment);
        this.projectiles = new ProjectileRenderer(assets, lawn, interpolator, sprites);
        this.collectibles = new CollectibleRenderer(sprites, lawn, interpolator, clocks);
        this.mowers = new LawnmowerRenderer(sprites, lawn, interpolator, clocks, environment);
        this.vases = new VaseRenderer(sprites, lawn, clocks, new views.gdx.ui.UiArt(assets));
        this.nuts = new BowlingRenderer(sprites, lawn, clocks, interpolator);
        this.brains = new IZombieRenderer(assets, lawn);
        this.beghouled = new BeghouledRenderer(assets, lawn);
        // Beghouled's pieces fall into their cells; every other board's plants are simply in theirs. The
        // lift answers 0 for both a cell at rest and every cell of a board that is not Beghouled at all,
        // so this costs an ordinary level one array bounds check per plant. See PlantRenderer.CellLift.
        this.plants.setLift(this.beghouled::liftAt);
        this.weather = new WeatherEffects(sprites, lawn);
        this.impacts = new ImpactEffects(sprites, lawn);
        this.explosions = new ExplosionEffects(sprites, lawn);
        this.explosions.setCollectibles(this.collectibles);
        this.bossEffects = new BossEffects(sprites, lawn);
        this.deaths = new DeathEffects(sprites, lawn);
        // A death asks the blasts whether one of them is what killed it -- that is what decides between
        // the zombie's own `die` clip and a heap of ash. See DeathEffects.
        this.deaths.setExplosions(this.explosions);
        // And where a killed zombie's head goes.
        this.deaths.setPieces(this.zombies.pieces());
        // Shared rather than a second instance: the head a corpse wears has to be measured and scaled
        // by exactly the thing that was drawing it a frame earlier, and both halves cache per clip.
        this.deaths.setBotany(this.zombies.botany());
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
        plants.sweepFlashes(delta);
        terrain.sweepFlashes();
        zombies.sweepFlashes(delta);
        zombies.resetFootCheck();
    }

    // The model narrates detonations; this is where the view hears about them.
    public ExplosionEffects explosions() {
        return explosions;
    }

    public BossEffects bossEffects() {
        return bossEffects;
    }

    // And the Gargantuar's imp throw, which is an instant rather than a state -- so it too arrives as a
    // sentence rather than as something readable off the board.
    public ZombieActions zombieActions() {
        return zombies.actions();
    }

    // And the Zombotany Peashooter's shot, which the head has to hear about to fire: the model damages
    // the plant down the lane in one call, so nothing on the board records that anything was spat.
    public ZombotanyHead botany() {
        return zombies.botany();
    }

    // And the bones a Tomb Raiser throws to bring a headstone up, which are drawn on the tile the
    // sentence names rather than on anything the board can be asked about.
    public TerrainRenderer terrain() {
        return terrain;
    }

    // The Hunter's snowball is the one impact that cannot be inferred from a vanished projectile,
    // because the model's ice throw never creates one. See ImpactEffects.onEvent.
    public ImpactEffects impacts() {
        return impacts;
    }

    // Same seam, same reason: the tornado and the freezing wind leave nothing on the board to infer
    // them from, so the model's own narration is what raises them.
    public WeatherEffects weather() {
        return weather;
    }

    // And what a zombie leaves behind, which is the strongest case of all: the model removes a dead
    // zombie on the tick it dies, so there is no frame in which it can be seen dying. See DeathEffects.
    public DeathEffects deaths() {
        return deaths;
    }

    private void drawLanes(Batch batch, GameSession session, float delta, float alpha) {
        // The blast's rear half, under everything it engulfs.
        explosions.drawRear(batch, delta);
        // Once per frame, before the lane pass visits it five times. See DeathEffects.advance.
        deaths.advance(delta);
        // Same reason, for the bones a Tomb Raiser throws at the ground.
        terrain.advanceEffects(delta);
        beghouled.advance(session, delta);

        for (int row = 0; row < Constants.BOARD_ROWS; row++) {
            drawLane(batch, session, session.getMap().getRow(row), row, delta, alpha);
        }
        finishFrame(batch, session, delta, alpha);
    }

    // Everything standing in one lane, back to front. Split out of drawLanes because the ORDER is the
    // whole content of this method and it has to be readable in one screen -- eight layers now, and
    // every one of them is a decision.
    private void drawLane(Batch batch, GameSession session, Row lane, int row, float delta,
                          float alpha) {
        mowers.draw(batch, lane.getLawnmower(), row, delta, alpha);
        // Where the mower would be, and never at the same time as one: I, Zombie removes the mowers
        // in onStart and the brain takes the slot.
        brains.drawRow(batch, session, row);

        // The water's edge, before the tiles either side of it.
        terrain.drawWaterline(batch, session, row, delta);

        // Save Our Seeds gilds the tiles it is defending. Under the plants, because it is the ground
        // they are standing on rather than a badge pinned to them.
        terrain.drawGuardedTiles(batch, session, row, delta);

        // Terrain before plants: a Grave Buster is planted ON a headstone and has to sit in front
        // of it, and ice blocks likewise wrap what is underneath.
        for (int col = 0; col < Constants.BOARD_COLS; col++) {
            Cell cell = lane.cellAt(col);
            terrain.drawCell(batch, cell, col, row, delta);
        }

        // Beghouled craters go down with the terrain: a hole a zombie chewed is ground, not something
        // standing on it, and a plant can never sit on one anyway.
        beghouled.drawCraters(batch, session, row);

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

        // And the flash on whatever a match just dropped into place, AFTER them, so it lights the piece
        // that arrived rather than the ground it landed on.
        beghouled.drawSettles(batch, session, row);

        // Vases stand in their tile the way a plant does, and a zombie coming out of one walks in
        // front of it, so they belong between the plants and the zombies. On any level that is not
        // Vasebreaker this costs a single instanceof.
        vases.drawRow(batch, session, row, delta);

        // The dead go down BEFORE the living, so the horde walks over its own casualties. Drawn after
        // them a corpse sits on top of the zombie stepping past it, which reads as the body being held
        // up rather than trampled. See DeathEffects.
        deaths.drawRow(batch, row);

        laneZombies.clear();
        for (Zombie zombie : lane.getZombies()) {
            // A Zomboss is a member of BOTH of the rows it straddles -- that is what lets plants in
            // either one shoot it (see Zombie.rowSpan) -- so this loop reaches it twice a frame. It is
            // drawn from its TOP row only, which is the row it is filed under and the one its two-lane
            // height is measured down from; drawn from the lower row as well it would appear a second
            // time, one lane down, ghosting through itself.
            if (zombie.rowSpan() > 1 && zombie.getMovement().getPositionY() != row) {
                continue;
            }
            laneZombies.add(zombie);
        }
        laneZombies.sort(BACK_TO_FRONT);
        for (Zombie zombie : laneZombies) {
            zombies.draw(batch, zombie, delta, alpha);
        }

        // The pieces coming off the living -- a broken cone, a shed arm -- in front of the zombies they
        // came off, because they are leaving the body toward the viewer. See Dismemberment.
        zombies.pieces().drawRow(batch, row);

        // After the zombies, because a bowling nut rolls OVER the ones it has already flattened and
        // into the ones it has not -- drawn under them it disappears the moment it reaches the horde,
        // which is the moment the player is watching it.
        nuts.drawRow(batch, session, row, delta, alpha);

        // The front half of an ice block, so whatever is frozen sits INSIDE it. Its rear half went
        // down with the terrain, before the plants -- that is how the two-part art is meant to be
        // used, and one layer alone puts the ice on top of its prisoner like a sticker.
        for (int col = 0; col < Constants.BOARD_COLS; col++) {
            terrain.drawCellFront(batch, lane.cellAt(col), col, row, delta);
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

    // What is left once every lane has been drawn: spent shots become impacts, the suns go over the
    // whole board, and the blast's front half goes over those.
    private void finishFrame(Batch batch, GameSession session, float delta, float alpha) {
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

        // Weather is in front of the board it is happening to: a sandstorm the zombies walk over is not
        // a sandstorm. Under the blast, which is the loudest thing on any frame it appears in.
        weather.draw(batch, delta);

        // The blast's front half, over everything -- drawn after the suns so a detonation is not hidden
        // behind one that happens to be sitting on the same tile.
        explosions.drawFront(batch);

        // And a boss attack over even that. It is the single loudest thing that can happen on a frame,
        // it wipes whole rows, and half of it hidden behind the plants it just destroyed would leave the
        // player looking at an empty lane with no idea what emptied it.
        bossEffects.draw(batch, delta);
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
            // A muzzle flash IS "a plant just fired", which is the trigger the shooting sound needs and
            // the only place the view already computes it. The plant's own sound if there is a file for
            // it, otherwise the generic pea. AudioManager collapses repeats inside 50ms, so a row of
            // Peashooters firing on one frame is one pea rather than eight stacked copies of one
            // waveform -- but two DIFFERENT plants firing together are still two sounds, because the
            // cooldown is keyed on whichever name won.
            if (audio != null) {
                audio.play(views.gdx.core.AudioManager.forEntity(
                        views.gdx.core.AudioManager.SFX_SHOOT, muzzle.shooter()),
                        views.gdx.core.AudioManager.SFX_SHOOT);
            }
        }
        for (PlantRenderer.Strike strike : plants.drainStrikes()) {
            impacts.spawnStrike(strike.fromX(), strike.fromY(), strike.toX(), strike.toY(),
                    strike.sprite());
        }
    }
}
