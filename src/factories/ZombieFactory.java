package factories;

import factories.zombie.ZombieBehaviorFactory;
import models.entities.zombies.Abilities.ZombieAbility;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import models.templates.ZombieTemplate;
import utils.Constants;
import utils.registry.ZombieRegistry;

import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

// Spawns a live Zombie from a registered blueprint. The template's core stats map straight onto the
// component-based Zombie; the objclass selects the ability set (see ZombieBehaviorFactory), and the
// armor list plus baseHp are assembled into the layered HealthComponent inside the Zombie.
//
// TODO: scale zombie HP / eat damage by the player's difficulty level.
public final class ZombieFactory {
    private static final AtomicInteger ID_SEQUENCE = new AtomicInteger(1);
    private static final Random RANDOM = new Random();

    private ZombieFactory() { }

    public static Zombie createZombie(String alias, double x, int y, GameSession gameSession) {
        ZombieTemplate template = ZombieRegistry.getInstance().getZombieTemplateByAlias(alias);
        if (template == null) {
            return null;
        }

        List<ZombieAbility> abilities = ZombieBehaviorFactory.createAbilities(template.getObjclass(), gameSession);

        // EatDPS is modelled as one bite per second dealing that much damage.
        int eatDamage = template.getEatDps();
        int eatSpeed = Constants.TICKS_PER_SECOND;

        // 5% of the zombies that walk on glow, and a glowing one hands the player a plant food when it
        // dies. Rolled here because this is the one place zombies are born, so an ability that spawns
        // one mid-level gets the same odds as a wave does. Only blueprints that allow it can glow:
        // CanSpawnPlantFood is false for Gargantuars, Imps and the Dark Imp Dragon, which never carry
        // plant food.
        boolean glowing = template.isCanSpawnPlantFood()
                && RANDOM.nextDouble() < Constants.GLOWING_ZOMBIE_PROBABILITY;

        return new Zombie(
                ID_SEQUENCE.getAndIncrement(),
                categoryOf(template.getObjclass()),
                template.getBaseHp(),
                template.getArmors(),
                template.getAlias(),
                eatDamage,
                eatSpeed,
                template.getSpeed(),
                x,
                y,
                template.isCanSpawnPlantFood(),
                abilities,
                template.getWavePointCost(),
                glowing,
                gameSession);
    }

    // Normalizes an objclass into a short category token ("ZombieGargantuarProps" -> "Gargantuar"),
    // so consumers like Electric Blueberry's PRIORITIZE_GARGANTUARS can match on it.
    private static String categoryOf(String objclass) {
        if (objclass == null) {
            return "Basic";
        }
        String category = objclass;
        if (category.startsWith("Zombie")) {
            category = category.substring("Zombie".length());
        }
        if (category.endsWith("Props")) {
            category = category.substring(0, category.length() - "Props".length());
        }
        if (category.endsWith("PropertySheet")) {
            category = category.substring(0, category.length() - "PropertySheet".length());
        }
        return category.isEmpty() ? "Basic" : category;
    }
}
