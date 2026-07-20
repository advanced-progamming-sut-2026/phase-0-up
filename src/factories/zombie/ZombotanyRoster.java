package factories.zombie;

import models.templates.ZombieTemplate;
import utils.registry.ZombieRegistry;

import java.util.ArrayList;

// Registers the four Zombotany "plant-zombie" blueprints (Peashooter, Wall-nut, Jalapeno, Squash) into
// the shared ZombieRegistry so a Zombotany level's waves can spawn them by alias. Their stats are based
// on the ordinary walker: the Wall-nut zombie is far tankier, the Squash zombie much faster, and each
// carries the matching objclass that ZombieBehaviorFactory turns into its special ability.
public final class ZombotanyRoster {
    public static final String PEASHOOTER = "ZombieBotanyPeashooter";
    public static final String WALLNUT = "ZombieBotanyWallnut";
    public static final String JALAPENO = "ZombieBotanyJalapeno";
    public static final String SQUASH = "ZombieBotanySquash";

    private static final int FALLBACK_HP = 200;
    private static final double FALLBACK_SPEED = 0.25;
    private static final int FALLBACK_EAT = 100;
    private static final int FALLBACK_COST = 1;

    private ZombotanyRoster() { }

    public static void register() {
        ZombieRegistry registry = ZombieRegistry.getInstance();
        if (registry.getZombieTemplateByAlias(PEASHOOTER) != null) {
            return;   // idempotent -- register once
        }
        ZombieTemplate base = registry.getZombieTemplateByAlias("ZombieDefault");
        int hp = base != null && base.getBaseHp() > 0 ? base.getBaseHp() : FALLBACK_HP;
        double speed = base != null && base.getSpeed() > 0 ? base.getSpeed() : FALLBACK_SPEED;
        int eat = base != null && base.getEatDps() > 0 ? base.getEatDps() : FALLBACK_EAT;
        int cost = base != null && base.getWavePointCost() > 0 ? base.getWavePointCost() : FALLBACK_COST;

        registry.register(new ZombieTemplate(PEASHOOTER, "ZombieBotanyPeashooterProps",
                hp, speed, eat, cost, true, new ArrayList<>()));
        registry.register(new ZombieTemplate(WALLNUT, "ZombieBotanyWallnutProps",
                hp * 6, speed, eat, cost * 2, true, new ArrayList<>()));
        registry.register(new ZombieTemplate(JALAPENO, "ZombieBotanyJalapenoProps",
                hp, speed, eat, cost, false, new ArrayList<>()));
        registry.register(new ZombieTemplate(SQUASH, "ZombieBotanySquashProps",
                hp, speed * 2.5, eat, cost, false, new ArrayList<>()));
    }
}
