package views.gdx.sprite;

import models.entities.zombies.Components.ActionState;
import models.entities.zombies.Zombie;

// Chooses which animation clip an entity should be playing.
//
// The asset dump is not uniform. Most zombies expose idle/walk/eat/die, but the Newspaper Zombie has
// walk_newspaper and eat_newspaper for as long as it still holds the paper, the Jester has spin_walk
// while spinning, and a few have no eat clip at all. Rather than special-case those at each call site,
// every lookup is a preference list resolved against what the animation actually defines.
public final class ClipMap {

    public static final String IDLE = "idle";

    // Clips that should repeat forever versus play once and hold.
    //
    // A .PAM clip does not loop by itself: played past its end it simply stops moving. That is why a
    // zombie appeared to walk only until it reached the board (it enters at ~2.4s and the walk clip is
    // 3.0s long) and then slid along frozen mid-stride.
    //
    // The inverse mistake is just as visible: a headstone's damage poses are one-shot crumbles, and
    // repeating them makes the stone flicker as it re-crumbles several times a second.
    private static final java.util.Set<String> ONE_SHOT_PREFIXES = java.util.Set.of(
            "die", "damage", "undamaged", "attack", "special", "shooting", "plantfood",
            "smash", "fire", "spawn", "enter", "exit", "explosion", "splat");

    public static boolean loops(String clip) {
        if (clip == null) {
            return true;
        }
        for (String prefix : ONE_SHOT_PREFIXES) {
            if (clip.startsWith(prefix)) {
                return false;
            }
        }
        return true;
    }

    // Converts elapsed time into the time to hand PamPlayer: wrapped for looping clips, held just
    // short of the end for one-shot ones. Falls back to raw elapsed time when the duration is unknown.
    public static float sample(EntitySprite sprite, String clip, float elapsed) {
        float duration = sprite.clipDuration(clip);
        if (duration <= 0f) {
            return elapsed;
        }
        if (loops(clip)) {
            // libPVZ does NOT repeat a clip on its own: played past its end it holds the final frame.
            // That is precisely why zombies appeared to walk only until they reached the board -- they
            // spawn off-board and enter at ~2.4s, while the walk clip is 3.0s long, so it ran out just
            // as they arrived and they slid on frozen mid-stride.
            //
            // (Two frames 6s apart being pixel-identical is what a CLAMPED clip looks like too, not
            // just a looping one. Wrapping is what actually makes it cycle.)
            return elapsed % duration;
        }
        // One-shot clips are the ones that need help: left to loop, a headstone's damage pose
        // re-crumbles several times a second, which is the blinking. Hold on the final frame instead,
        // a hair inside the clip so it does not wrap round to the first.
        return Math.min(elapsed, duration - 0.0001f);
    }

    private ClipMap() { }

    // First candidate the sprite actually has, falling back to idle and finally to the first candidate
    // so the caller always gets a non-null name.
    public static String firstAvailable(EntitySprite sprite, String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && sprite.hasClip(candidate)) {
                return candidate;
            }
        }
        if (sprite.hasClip(IDLE)) {
            return IDLE;
        }
        return candidates.length > 0 ? candidates[0] : IDLE;
    }

    // What a zombie should be playing right now, given its action and what it is still carrying.
    public static String forZombie(EntitySprite sprite, Zombie zombie) {
        ActionState action = zombie.getState().getCurrentAction();
        boolean holdsNewspaper = hasNewspaper(zombie);
        boolean spinning = zombie.getState().isSpinning();

        return switch (action) {
            case EATING -> holdsNewspaper
                    ? firstAvailable(sprite, "eat_newspaper", "eat")
                    : firstAvailable(sprite, "eat");
            case DYING -> firstAvailable(sprite, "die");
            case IDLE -> firstAvailable(sprite, IDLE);
            // A lane switch is a hop in the model with no dedicated art; keep walking through it.
            case WALKING, LANE_SWITCHING -> {
                if (spinning) {
                    yield firstAvailable(sprite, "spin_walk", "spin", "walk");
                }
                yield holdsNewspaper
                        ? firstAvailable(sprite, "walk_newspaper", "walk")
                        : firstAvailable(sprite, "walk", IDLE);
            }
        };
    }

    // The newspaper is armor in the model, so "still has it" means the layer is still on the stack.
    private static boolean hasNewspaper(Zombie zombie) {
        return zombie.getHealth().getLayers().stream()
                .anyMatch(layer -> layer.getType()
                        == models.entities.zombies.Components.ArmorType.NEWSPAPER);
    }
}
