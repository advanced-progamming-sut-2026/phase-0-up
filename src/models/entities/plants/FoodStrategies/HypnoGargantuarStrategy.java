package models.entities.plants.FoodStrategies;

import models.entities.plants.Plant;
import models.entities.plants.abilities.HypnotizeOnEatenAbility;
import models.entities.plants.abilities.PlantAbility;
import models.game.GameSession;

// HYPNO_GARGANTUAR plant food: the next zombie to bite this shroom swells into a Gargantuar and fights
// for the player.
//
// Arms rather than acts. Hypno-shroom's whole effect happens when something EATS it, and at the moment
// the plant food goes in there may be nothing on the lawn at all -- so the boost is stored on the
// ability and spent by the bite. The plant glows until then, which is the only way to tell a loaded
// shroom from an ordinary one.
//
// This replaced RANDOM_HYPNOTIZE, which turned one zombie somewhere on the board without the shroom
// being touched -- correct as an effect, but not this plant's.
public class HypnoGargantuarStrategy implements PlantFoodStrategy {

    @Override
    public void executeEffect(Plant sourcePlant, GameSession gameSession) {
        for (PlantAbility ability : sourcePlant.getAbilities()) {
            if (ability instanceof HypnotizeOnEatenAbility hypno) {
                hypno.armGargantuar();
                return;
            }
        }
    }
}
