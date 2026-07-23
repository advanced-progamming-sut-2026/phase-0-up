package factories;

import controllers.systems.game.EnvironmentSystem;
import factories.zombie.ZombieBehaviorFactory;
import models.entities.zombies.Abilities.ZombieAbility;
import models.entities.zombies.Zombie;
import models.game.EnvironmentType;
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
// Body HP and bite damage are scaled by the player's difficulty level here -- the one place every
// spawn path funnels through, so a wave zombie, a cheat spawn and an ability-summoned Imp are all
// scaled identically. See difficultyScale().
public final class ZombieFactory {
    private static final AtomicInteger ID_SEQUENCE = new AtomicInteger(1);
    private static final Random RANDOM = new Random();

    private ZombieFactory() { }

    public static Zombie createZombie(String alias, double x, int y, GameSession gameSession) {
        ZombieTemplate template = ZombieRegistry.getInstance().getZombieTemplateByAlias(alias);
        if (template == null) {
            return null;
        }

        List<ZombieAbility> abilities = ZombieBehaviorFactory.createAbilities(
                template.getObjclass(), template.getAlias(), gameSession);

        // EatDPS is modelled as one bite per second dealing that much damage. Both it and the body HP
        // are scaled by difficulty, so a harder game fields tougher zombies that chew faster, not just
        // more of them (that part is WaveSystem's budget).
        double scale = difficultyScale(gameSession);
        int eatDamage = scaled(template.getEatDps(), scale);
        int baseHp = scaled(template.getBaseHp(), scale);
        int eatSpeed = Constants.TICKS_PER_SECOND;

        // 5% of the zombies that walk on glow, and a glowing one hands the player a plant food when it
        // dies. Rolled here because this is the one place zombies are born, so an ability that spawns
        // one mid-level gets the same odds as a wave does. Only blueprints that allow it can glow:
        // CanSpawnPlantFood is false for Gargantuars, Imps and the Dark Imp Dragon, which never carry
        // plant food.
        boolean glowing = template.isCanSpawnPlantFood()
                && RANDOM.nextDouble() < Constants.GLOWING_ZOMBIE_PROBABILITY;

        Zombie zombie = new Zombie(
                ID_SEQUENCE.getAndIncrement(),
                categoryOf(template.getObjclass()),
                baseHp,
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

        // Frostbite Caves rule: every zombie here shrugs off the "frozen" effect from ice attacks.
        // Set once at birth so it covers wave spawns and any zombie an ability spawns mid-level.
        if (EnvironmentSystem.environmentOf(gameSession) == EnvironmentType.FROSTBITE_CAVES) {
            zombie.getState().setFreezeImmune(true);
        }

        // A zombie has just entered the level: record it as discovered (first sighting posts a news
        // entry and raises the unread-news badge). This is the single choke point every spawn path
        // funnels through, so no caller has to remember to do it.
        if (gameSession != null) {
            gameSession.discoverZombie(template.getAlias());
        }
        return zombie;
    }

    // How much tougher (or softer) a zombie is than the blueprint says, from the player's difficulty.
    // Mirrors WaveSystem's and SunSystem's handling so the three scale off one baseline: the default
    // level returns exactly 1.0, which is what keeps every authored stat meaning what it says for a
    // player who never touches the setting.
    private static double difficultyScale(GameSession gameSession) {
        return difficultyLevel(gameSession) / (double) Constants.DEFAULT_DIFFICULTY_LEVEL;
    }

    private static int difficultyLevel(GameSession gameSession) {
        if (gameSession == null || gameSession.getPlayer() == null
                || gameSession.getPlayer().getDifficultyLevel() <= 0) {
            return Constants.DEFAULT_DIFFICULTY_LEVEL;
        }
        return gameSession.getPlayer().getDifficultyLevel();
    }

    // Never rounds a positive stat down to zero -- a zombie with 0 HP would be born dead, and one that
    // bites for 0 could never eat a plant.
    private static int scaled(int base, double scale) {
        if (base <= 0) {
            return base;
        }
        return Math.max(1, (int) Math.round(base * scale));
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
