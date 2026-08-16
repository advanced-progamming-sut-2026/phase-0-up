package views.gdx.bridge;

import models.entities.collectibles.Sun;
import models.entities.projectiles.Projectile;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import models.map.Lawnmower;
import models.map.Row;

import java.util.IdentityHashMap;
import java.util.Map;

// Makes a 10 Hz simulation look like 60 fps motion.
//
// The model advances in whole ticks and a zombie covers ~2.2 world px per tick. Drawn straight from
// the model, everything on the lawn would jump six times a second. So each tick's start and end
// positions are remembered, and the renderer draws somewhere between them according to how far into
// the current tick we are.
//
// Keyed on IDENTITY, not on Entity.getId(). Every Projectile is constructed with id = 0
// (`super(type.toString(), 0, x, ...)`), so an id-keyed map would collapse every pea in flight into
// one entry and they would all draw on top of each other.
public final class EntityInterpolator {

    private static final class Track {
        float prevX;
        float prevY;
        float currX;
        float currY;
        boolean seenThisTick;
        boolean hasPrevious;
    }

    private final Map<Object, Track> tracks = new IdentityHashMap<>();

    // Called immediately BEFORE the model advances: this tick's positions become last tick's.
    public void beginTick() {
        for (Track track : tracks.values()) {
            track.prevX = track.currX;
            track.prevY = track.currY;
            track.hasPrevious = true;
            track.seenThisTick = false;
        }
    }

    // Called immediately AFTER the model advances: read every live entity's new position, then drop
    // the tracks of anything that has left the board so the map cannot grow without bound over a long
    // level.
    public void endTick(GameSession session) {
        sampleAll(session);
        tracks.entrySet().removeIf(entry -> !entry.getValue().seenThisTick);
    }

    // Seeds the first tick, so nothing interpolates from a phantom origin at 0,0 on its first frame.
    public void prime(GameSession session) {
        sampleAll(session);
        for (Track track : tracks.values()) {
            track.prevX = track.currX;
            track.prevY = track.currY;
            track.hasPrevious = true;
        }
    }

    private void sampleAll(GameSession session) {
        if (session == null || session.getMap() == null) {
            return;
        }
        for (Row row : session.getMap().getRows()) {
            for (Zombie zombie : row.getZombies()) {
                // Lane is sampled as a float because a lane switch is an instant jump in the model;
                // interpolating it turns the hop into a slide.
                sample(zombie, (float) zombie.getMovement().getPositionX(),
                        zombie.getMovement().getPositionY());
            }
            for (Projectile projectile : row.getActiveProjectiles()) {
                // getExactY, not getY: a diagonal shot moves half a lane per tick and getY() is the
                // rounded row it is filed under. Sampling that tracked a Rotobaga's shots as 0, 1, 1,
                // 2 -- whole-lane jumps -- so there was nothing between the lanes to interpolate and
                // the diagonals crossed the board sideways. Same reason suns sample getCurrentY().
                sample(projectile, (float) projectile.getX(), (float) projectile.getExactY());
            }
            Lawnmower mower = row.getLawnmower();
            if (mower != null) {
                sample(mower, (float) mower.getPositionX(), row.getIndex());
            }
        }
        // Suns live on the session, not on the map: GameMap.getActiveCollectibles() exists but
        // SunSystem never fills it.
        for (Sun sun : session.getActiveSuns()) {
            // A falling sun's precise height lives in Sun.currentY; Entity.getY() is the rounded row
            // and would make it drop a whole lane at a time.
            sample(sun, (float) sun.getX(), (float) sun.getCurrentY());
        }
    }

    private void sample(Object key, float x, float y) {
        Track track = tracks.get(key);
        if (track == null) {
            track = new Track();
            track.prevX = x;
            track.prevY = y;
            tracks.put(key, track);
        }
        track.currX = x;
        track.currY = y;
        track.seenThisTick = true;
    }

    // Interpolated position. alpha is how far through the current tick we are, 0..1.
    //
    // Falls back to the entity's own current value when it has no history yet -- something that spawned
    // mid-tick must appear where the model says it is, not slide in from wherever it happens to be
    // tracked from.
    public float x(Object key, double modelX, float alpha) {
        Track track = tracks.get(key);
        if (track == null || !track.hasPrevious) {
            return (float) modelX;
        }
        return track.prevX + (track.currX - track.prevX) * alpha;
    }

    public float lane(Object key, int modelLane, float alpha) {
        Track track = tracks.get(key);
        if (track == null || !track.hasPrevious) {
            return modelLane;
        }
        return track.prevY + (track.currY - track.prevY) * alpha;
    }

    public void clear() {
        tracks.clear();
    }

    public int trackedCount() {
        return tracks.size();
    }
}
