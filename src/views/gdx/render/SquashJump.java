package views.gdx.render;

import models.entities.plants.Plant;
import models.entities.plants.abilities.PlantAbility;
import models.entities.plants.abilities.SquashAbility;
import views.gdx.sprite.EntitySprite;

// Which frame of its jump a Squash is on.
//
// Lives apart from PlantRenderer's usual action-clip machinery because a squash is the one plant whose
// action is TWO clips back to back -- it goes up and it comes down -- and that machinery plays exactly
// one clip per action and then returns to idle. Rather than grow a second stage machine beside the
// plant-food one, the leap is drawn straight off the model's own progress through it.
//
// That coupling is the point. The alternative is a clock in the view, and a clock in the view can drift
// out of step with the model: the squash would be drawn still rising while the thing underneath it had
// already been flattened, or hanging in the air after the plant was gone.
//
// The dump ships six clips for this and nothing was playing any of them -- Squash was a 0x0 explosion,
// so it went up in a Cherry Bomb fireball instead. See SquashAbility.
final class SquashJump {

    private SquashJump() { }

    // Up for the first half of the leap, down for the second.
    private static final float DESCENT_STARTS = 0.5f;

    static SquashAbility leapingAbility(Plant plant) {
        if (plant == null || plant.getAbilities() == null) {
            return null;
        }
        for (PlantAbility ability : plant.getAbilities()) {
            if (ability instanceof SquashAbility squash && squash.isWindingUp()) {
                return squash;
            }
        }
        return null;
    }

    // The clip for where this squash is in its jump, or null if the art has no such clip -- in which
    // case the caller falls back to its usual choice rather than drawing nothing.
    static String clipFor(EntitySprite sprite, SquashAbility squash, float progress) {
        if (sprite == null || !sprite.isReady()) {
            return null;
        }
        String side = squash.isFacingRight() ? "right" : "left";
        boolean descending = progress >= DESCENT_STARTS;

        // A plant-food leap has its own descent in the art, and only a descent -- the rise is shared
        // with an ordinary jump.
        if (descending && squash.isBoostedLeap()) {
            String boosted = "plantfood_jump_down_" + side;
            if (sprite.hasClip(boosted)) {
                return boosted;
            }
        }
        String wanted = (descending ? "jump_down_" : "jump_up_") + side;
        return sprite.hasClip(wanted) ? wanted : null;
    }

    // Seconds into that clip. The leap's half is stretched across the clip's WHOLE length, so the
    // animation always plays out in full however long SquashAbility spends in the air -- retune the
    // leap and the jump still lands on its last frame rather than being cut off partway.
    static float phaseFor(EntitySprite sprite, String clip, float progress) {
        float half = progress < DESCENT_STARTS
                ? progress / DESCENT_STARTS
                : (progress - DESCENT_STARTS) / (1f - DESCENT_STARTS);
        float duration = sprite.clipDuration(clip);
        if (duration <= 0f) {
            return half;
        }
        // A hair inside the end, so the last frame is shown rather than wrapping back to the first.
        return Math.min(half, 0.9999f) * duration;
    }
}
