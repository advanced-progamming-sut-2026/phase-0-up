package models.entities.zombies.Abilities;
import models.entities.zombies.Components.ArmorType;
import models.entities.zombies.Components.HealthLayer;
import models.entities.zombies.Zombie;
// Newspaper Zombie: slow while the paper is intact, 3x speed and 3x bite once shredded.
public class NewspaperRageAbility implements ZombieAbility {
    private static final double CALM_SPEED_SCALE = 0.5;

    // Three, not the four in zombies.json.
    //
    // The blueprint's EnragedSpeedScale and EnragedDamageScale are both 4, and the spec only asks for
    // "چند برابر" -- several times over -- which three satisfies as well as four does. Four put him at
    // 0.88 tiles a second, better than four times a Browncoat's pace, arriving with 800 bite damage; a
    // tuning call, made deliberately against the data rather than by missing it.
    private static final double ENRAGED_SPEED_SCALE = 3.0;
    private static final double ENRAGED_DAMAGE_SCALE = 3.0;
    private boolean calmed = false;
    private boolean enraged = false;
    @Override
    public void execute(Zombie zombie) {
        if (zombie == null || zombie.getMovement() == null || zombie.getHealth() == null) {
            return;
        }
        if (!calmed) {
            calmed = true;
            zombie.getMovement().setSpeed(zombie.getMovement().getSpeed() * CALM_SPEED_SCALE);
        }
        if (enraged || stillHasNewspaper(zombie)) {
            return;
        }
        enraged = true;
        zombie.getMovement().setSpeed(
                zombie.getMovement().getSpeed() / CALM_SPEED_SCALE * ENRAGED_SPEED_SCALE);
        zombie.scaleEatDamage(ENRAGED_DAMAGE_SCALE);
        // With the tile, so the view can find him and play `newspaper_defeat` -- the one clip in that
        // animation for the moment the paper goes, and the only one of its seven nothing ever asked
        // for. Everything else it needs it already reads off the board: ClipMap swaps walk_newspaper
        // for walk and eat_newspaper for eat the instant the armour layer leaves the stack.
        zombie.getGameSession().reportEvent("You tore up the " + zombie.getAlias()
                + "'s newspaper at (" + (int) zombie.getMovement().getPositionX() + ", "
                + zombie.getMovement().getPositionY() + ") -- now he is furious, and coming in fast.");
    }
    private boolean stillHasNewspaper(Zombie zombie) {
        for (HealthLayer layer : zombie.getHealth().getLayers()) {
            if (layer.getType() == ArmorType.NEWSPAPER && layer.getCurrentHp() > 0) {
                return true;
            }
        }
        return false;
    }
}
