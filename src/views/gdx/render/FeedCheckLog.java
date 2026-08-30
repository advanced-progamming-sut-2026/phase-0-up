package views.gdx.render;

import models.entities.plants.Plant;

import java.util.IdentityHashMap;
import java.util.Map;

// The -Dpvz.feedCheck trace: one line each time a fed plant moves through the plant-food sequence, and
// one when its boost is over saying how long the effect ran against how long the plant was drawn
// boosted. That comparison is the whole point of the flag -- it is how the "the animation stops before
// the shots do" class of bug is caught without watching for it.
//
// Split out of PlantRenderer because it is diagnostics rather than drawing: it holds one map of its
// own, it is inert unless the flag is set, and PlantRenderer is at its size limit.
final class FeedCheckLog {

    // The stage each fed plant was last seen in, so a change is logged once rather than every frame.
    private final Map<Plant, Integer> reportedStage = new IdentityHashMap<>();

    void stage(Plant plant, int stage, String clip, float phase, boolean boostRunning) {
        if (!views.gdx.core.DebugFlags.FEED_CHECK) {
            return;
        }
        Integer seen = reportedStage.put(plant, stage);
        if (seen != null && seen == stage) {
            return;
        }
        com.badlogic.gdx.Gdx.app.log("FeedCheck", String.format(
                "%s %s at %.2fs  boostRunning=%s", plant.getName(), clip, phase, boostRunning));
    }

    // drawn is how long this plant has been drawn boosted; stopped is the reading of that same clock
    // at the moment the model stopped calling the boost active, or null if it never did.
    void end(Plant plant, Float drawn, Float stopped) {
        if (!views.gdx.core.DebugFlags.FEED_CHECK) {
            return;
        }
        reportedStage.remove(plant);
        if (drawn == null) {
            return;
        }
        com.badlogic.gdx.Gdx.app.log("FeedCheck", String.format(
                "%s DONE  boost ran %s,  drawn boosted %.2fs",
                plant.getName(),
                stopped == null ? "past the animation" : String.format("%.2fs", stopped),
                drawn));
    }

    void forget(Plant plant) {
        reportedStage.remove(plant);
    }
}
