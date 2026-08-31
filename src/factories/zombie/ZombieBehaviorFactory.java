package factories.zombie;

import models.entities.zombies.Abilities.ArcadePushAbility;
import models.entities.zombies.Abilities.CarryADynamite;
import models.entities.zombies.Abilities.ChangeRow;
import models.entities.zombies.Abilities.DeflectLobbedAbility;
import models.entities.zombies.Abilities.FireImmunityAbility;
import models.entities.zombies.Abilities.IceImmunityAbility;
import models.entities.zombies.Abilities.EatPlantAbility;
import models.entities.zombies.Abilities.FishThePlants;
import models.entities.zombies.Abilities.FootballTackleAbility;
import models.entities.zombies.Abilities.IgnoreObstaclesAbility;
import models.entities.zombies.Abilities.JalapenoBurnAbility;
import models.entities.zombies.Abilities.KillPlantsAbility;
import models.entities.zombies.Abilities.LaserBeamAbility;
import models.entities.zombies.Abilities.NewspaperRageAbility;
import models.entities.zombies.Abilities.ShootingAbility;
import models.entities.zombies.Abilities.SquashCrushAbility;
import models.entities.zombies.Abilities.PianoCrushAbility;
import models.entities.zombies.Abilities.PushIceAbility;
import models.entities.zombies.Abilities.SpinAbility;
import models.entities.zombies.Abilities.StealGroundSunAbility;
import models.entities.zombies.Abilities.StealSunAbility;
import models.entities.zombies.Abilities.SubmergeAbility;
import models.entities.zombies.Abilities.SummonGraveAbility;
import models.entities.zombies.Abilities.ThrowIceAbility;
import models.entities.zombies.Abilities.ThrowImp;
import models.entities.zombies.Abilities.ThrowOctopusAbility;
import models.entities.zombies.Abilities.TurnIntoSheep;
import models.entities.zombies.Abilities.TurnIntoKnightAbility;
import models.entities.zombies.Abilities.ZombieAbility;
import models.entities.zombies.Abilities.ZombieDuelAbility;
import models.game.GameSession;

import java.util.ArrayList;
import java.util.List;

// Maps a zombie's objclass to the set of abilities it should spawn with. Fully wired for the common
// walkers/armored/gargantuar/torch/wizard set (and the exotic types that already have ability
// classes); the remaining exotic mechanics attach a clearly-marked STUB ability. Every eater also
// gets EatPlantAbility so it can chew through defences.
public final class ZombieBehaviorFactory {
    private static final double SMASH_REACH = 0.7;
    private static final double TORCH_REACH = 1.0;
    private static final double RA_SUN_RADIUS = 3.0;
    // Ra's carrying capacity (blueprint MaxClaimedSunCurrency); all of it returns when it dies.
    private static final int RA_MAX_CLAIMED_SUN = 250;
    private static final double TURQUOISE_SUN_RADIUS = 4.0;
    private static final int TURQUOISE_SUN_PER_SECOND = 25;

    private ZombieBehaviorFactory() { }

    // Kept so older callers still compile; the alias-aware overload is the real entry point.
    public static List<ZombieAbility> createAbilities(String objclass, GameSession gameSession) {
        return createAbilities(objclass, null, gameSession);
    }

    // The alias is needed as well as the objclass because a few blueprints share the generic
    // "ZombiePropertySheet" objclass with the plain walker -- the Imp Dragon among them -- so their
    // signature behaviour can only be identified by name.
    public static List<ZombieAbility> createAbilities(String objclass, String alias,
                                                      GameSession gameSession) {
        List<ZombieAbility> byAlias = aliasAbilities(alias);
        if (byAlias != null) {
            return byAlias;
        }
        if (objclass == null) {
            return abilities(new EatPlantAbility());
        }
        // Grouped by the chapter a zombie belongs to, so each switch stays inside the 50-line method
        // limit. Each helper returns null for an objclass it does not own, and the last one supplies
        // the plain-walker default.
        List<ZombieAbility> result = egyptAndDarkAges(objclass, gameSession);
        if (result == null) {
            result = beachAndFrostbite(objclass);
        }
        if (result == null) {
            result = lostCityAndModern(objclass, gameSession);
        }
        return result != null ? result : abilities(new EatPlantAbility());
    }

    // Ancient Egypt and Dark Ages.
    private static List<ZombieAbility> egyptAndDarkAges(String objclass, GameSession gameSession) {
        switch (objclass) {
            case "ZombieGargantuarProps":
                return abilities(new KillPlantsAbility(false, SMASH_REACH), new ThrowImp());
            // Ra robs the LAWN, not the bank: it drags loose sun tokens in and pockets them. Nothing to
            // do with the Turquoise's StealSunAbility, which drains the player's stored sun.
            case "ZombieRaProps":
                return abilities(new EatPlantAbility(),
                        new StealGroundSunAbility(RA_SUN_RADIUS, RA_MAX_CLAIMED_SUN));
            case "ZombieExplorerProps":
                return abilities(new EatPlantAbility(), new KillPlantsAbility(true, TORCH_REACH));
            case "ZombieTombRaiserProps":
                return abilities(new EatPlantAbility(), new SummonGraveAbility());
            case "ZombieDarkWizardProps":
                return abilities(new EatPlantAbility(), new TurnIntoSheep());
            case "ZombieDarkKingProps":
                return abilities(new EatPlantAbility(), new TurnIntoKnightAbility());
            // Spins back STRAIGHT shots only; a parasol as well made it immune to everything.
            case "ZombieDarkJugglerProps":
                return abilities(new EatPlantAbility(), new SpinAbility());
            default:
                return null;
        }
    }

    // Big Wave Beach and Frostbite Caves.
    private static List<ZombieAbility> beachAndFrostbite(String objclass) {
        switch (objclass) {
            case "ZombieBeachSnorkelProps":
                return abilities(new EatPlantAbility(), new SubmergeAbility());
            case "ZombieBeachOctopusProps":
                return abilities(new EatPlantAbility(), new ThrowOctopusAbility());
            // FishThePlants first: it anchors the zombie, and must run even on an EATING tick.
            case "ZombieBeachFishermanProps":
                return abilities(new FishThePlants(), new EatPlantAbility());
            // Frostbite natives are at home in the cold: an ice hit neither freezes nor slows them, so
            // each carries IceImmunityAbility on top of its own trick.
            case "ZombieIceAgeHunterProps":
                return abilities(new EatPlantAbility(), new ThrowIceAbility(), new IceImmunityAbility());
            // Push before eat: EATING makes isUnableToMove() true, which blocked the ice crush.
            case "ZombieIceAgeTroglobiteProps":
                return abilities(new PushIceAbility(), new EatPlantAbility(), new IceImmunityAbility());
            // Obstacle check first: it sets the flying flag the eat pass reads in the same tick.
            case "ZombieIceAgeDodoProps":
                return abilities(new IgnoreObstaclesAbility(), new EatPlantAbility(),
                        new IceImmunityAbility());
            default:
                return null;
        }
    }

    // Lost City, Neon Mixtape / modern, and the Zombotany plant-zombies.
    private static List<ZombieAbility> lostCityAndModern(String objclass, GameSession gameSession) {
        switch (objclass) {
            case "ZombieLostCityJaneProps":
                return abilities(new EatPlantAbility(), new DeflectLobbedAbility());
            // The heist arms the laser; half the haul spills back on death.
            case "ZombieCrystalSkullProps":
                return abilities(new EatPlantAbility(),
                        new StealSunAbility(TURQUOISE_SUN_RADIUS, TURQUOISE_SUN_PER_SECOND,
                                gameSession),
                        new LaserBeamAbility());
            // Flattens rather than eats: EatPlantAbility's EATING state would halt the piano.
            case "ZombiePianoProps":
                return abilities(new PianoCrushAbility(), new ChangeRow());
            case "ZombieNewspaperProps":
                return abilities(new EatPlantAbility(), new NewspaperRageAbility());
            // The Prospector's dynamite blasts it to the far left of the lawn, from where it walks back
            // toward the house: left-most column, then heading reversed.
            case "ZombieProspectorProps":
                return abilities(new EatPlantAbility(), new CarryADynamite());
            // Push before eat (EATING blocks the crush). No RollTheBarrel -- Imps are the Roller's.
            case "ZombieArcadeProps":
                return abilities(new ArcadePushAbility(), new EatPlantAbility());
            case "ZombieModernAllStarProps":
                return abilities(new EatPlantAbility(), new FootballTackleAbility());
            // Zombotany plant-zombies: each carries the behaviour of the plant it mimics.
            case "ZombieBotanyPeashooterProps":
                return abilities(new EatPlantAbility(), new ShootingAbility());
            case "ZombieBotanyJalapenoProps":
                return abilities(new EatPlantAbility(), new JalapenoBurnAbility());
            case "ZombieBotanySquashProps":
                return abilities(new SquashCrushAbility());
            // ZombieBotanyWallnutProps is just a tanky walker (high HP set on the template), and
            // ZombiePropertySheet is the shared generic sheet -- both take the default.
            default:
                return null;
        }
    }

    // Blueprints whose objclass is the shared generic sheet, so only the alias identifies them.
    // Returns null when the alias has no special behaviour, letting the objclass switch decide.
    private static List<ZombieAbility> aliasAbilities(String alias) {
        if (alias == null) {
            return null;
        }
        String key = alias.toLowerCase();
        if (key.contains("impdragon")) {
            // The Imp Dragon breathes fire and swallows it: Projectile checks isImmuneToFire() to make
            // a fire shot bounce off it harmlessly, and nothing used to switch that flag on.
            return abilities(new EatPlantAbility(), new FireImmunityAbility());
        }
        return null;
    }

    // Every zombie funnels through here, which is why the duel ability is appended for all of them:
    // any zombie can end up hypnotized, and any zombie can be the one that turns on a hypnotized ally.
    // Appended LAST on purpose, so it sees the action state EatPlantAbility has already settled.
    private static List<ZombieAbility> abilities(ZombieAbility... items) {
        List<ZombieAbility> list = new ArrayList<>();
        for (ZombieAbility item : items) {
            list.add(item);
        }
        list.add(new ZombieDuelAbility());
        return list;
    }
}
