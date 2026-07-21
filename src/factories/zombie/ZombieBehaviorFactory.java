package factories.zombie;

import models.entities.zombies.Abilities.ArcadePushAbility;
import models.entities.zombies.Abilities.CarryADynamite;
import models.entities.zombies.Abilities.ChangeRow;
import models.entities.zombies.Abilities.DeflectLobbedAbility;
import models.entities.zombies.Abilities.FireImmunityAbility;
import models.entities.zombies.Abilities.IceImmunityAbility;
import models.entities.zombies.Abilities.RollTheBarrel;
import models.entities.zombies.Abilities.EatPlantAbility;
import models.entities.zombies.Abilities.FishThePlants;
import models.entities.zombies.Abilities.FootballTackleAbility;
import models.entities.zombies.Abilities.IgnoreObstaclesAbility;
import models.entities.zombies.Abilities.JalapenoBurnAbility;
import models.entities.zombies.Abilities.KillPlantsAbility;
import models.entities.zombies.Abilities.LaserBeamAbility;
import models.entities.zombies.Abilities.ShootingAbility;
import models.entities.zombies.Abilities.SquashCrushAbility;
import models.entities.zombies.Abilities.PianoCrushAbility;
import models.entities.zombies.Abilities.PushIceAbility;
import models.entities.zombies.Abilities.SpinAbility;
import models.entities.zombies.Abilities.StealSunAbility;
import models.entities.zombies.Abilities.SubmergeAbility;
import models.entities.zombies.Abilities.SummonGraveAbility;
import models.entities.zombies.Abilities.ThrowIceAbility;
import models.entities.zombies.Abilities.ThrowImp;
import models.entities.zombies.Abilities.ThrowOctopusAbility;
import models.entities.zombies.Abilities.TurnIntoCat;
import models.entities.zombies.Abilities.TurnIntoKnightAbility;
import models.entities.zombies.Abilities.ZombieAbility;
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
    private static final int RA_SUN_PER_SECOND = 25;

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
        switch (objclass) {
            case "ZombieGargantuarProps":
                return abilities(new KillPlantsAbility(false, SMASH_REACH), new ThrowImp());
            case "ZombieRaProps":
                return abilities(new EatPlantAbility(),
                        new StealSunAbility(RA_SUN_RADIUS, RA_SUN_PER_SECOND, true, gameSession));
            case "ZombieExplorerProps":
                return abilities(new EatPlantAbility(), new KillPlantsAbility(true, TORCH_REACH));
            case "ZombieTombRaiserProps":
                return abilities(new EatPlantAbility(), new SummonGraveAbility());
            case "ZombieDarkWizardProps":
                return abilities(new EatPlantAbility(), new TurnIntoCat());
            case "ZombieDarkKingProps":
                return abilities(new EatPlantAbility(), new TurnIntoKnightAbility());
            case "ZombieBeachSnorkelProps":
                return abilities(new EatPlantAbility(), new SubmergeAbility());
            case "ZombieBeachOctopusProps":
                return abilities(new EatPlantAbility(), new ThrowOctopusAbility());
            case "ZombieBeachFishermanProps":
                return abilities(new EatPlantAbility(), new FishThePlants());
            case "ZombieDarkJugglerProps":
                return abilities(new EatPlantAbility(), new SpinAbility(), new DeflectLobbedAbility());
            case "ZombieLostCityJaneProps":
                return abilities(new EatPlantAbility(), new DeflectLobbedAbility());
            // Frostbite natives are at home in the cold: an ice hit neither freezes nor slows them, so
            // each carries IceImmunityAbility on top of its own trick.
            case "ZombieIceAgeHunterProps":
                return abilities(new EatPlantAbility(), new ThrowIceAbility(), new IceImmunityAbility());
            case "ZombieIceAgeTroglobiteProps":
                return abilities(new EatPlantAbility(), new PushIceAbility(), new IceImmunityAbility());
            case "ZombieIceAgeDodoProps":
                return abilities(new EatPlantAbility(), new IgnoreObstaclesAbility(),
                        new IceImmunityAbility());
            case "ZombieCrystalSkullProps":
                return abilities(new EatPlantAbility(), new LaserBeamAbility());
            // The pianist crushes what it rolls over AND herds the zombies sharing its lane into the
            // neighbouring rows as it plays.
            case "ZombiePianoProps":
                return abilities(new EatPlantAbility(), new PianoCrushAbility(), new ChangeRow());
            // The Prospector's dynamite blasts it to the far left of the lawn, from where it walks back
            // toward the house: left-most column, then heading reversed.
            case "ZombieProspectorProps":
                return abilities(new EatPlantAbility(), new CarryADynamite());
            // The Arcade zombie shoves a machine ahead of it: it flattens what it rolls into, and once
            // the machine is wrecked the Imps riding inside spill out.
            case "ZombieArcadeProps":
                return abilities(new EatPlantAbility(), new ArcadePushAbility(), new RollTheBarrel());
            case "ZombieModernAllStarProps":
                return abilities(new EatPlantAbility(), new FootballTackleAbility());
            // Zombotany plant-zombies: each carries the behaviour of the plant it mimics.
            case "ZombieBotanyPeashooterProps":
                return abilities(new EatPlantAbility(), new ShootingAbility());
            case "ZombieBotanyJalapenoProps":
                return abilities(new EatPlantAbility(), new JalapenoBurnAbility());
            case "ZombieBotanySquashProps":
                return abilities(new SquashCrushAbility());
            case "ZombieBotanyWallnutProps":   // just a tanky walker (high HP set on the template)
            case "ZombiePropertySheet":
            default:
                return abilities(new EatPlantAbility());
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

    private static List<ZombieAbility> abilities(ZombieAbility... items) {
        List<ZombieAbility> list = new ArrayList<>();
        for (ZombieAbility item : items) {
            list.add(item);
        }
        return list;
    }
}
