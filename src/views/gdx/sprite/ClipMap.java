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
