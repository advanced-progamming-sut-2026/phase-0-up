package factories.zombie;

import models.entities.zombies.Abilities.ArcadePushAbility;
import models.entities.zombies.Abilities.DeflectLobbedAbility;
import models.entities.zombies.Abilities.EatPlantAbility;
import models.entities.zombies.Abilities.FishThePlants;
import models.entities.zombies.Abilities.FootballTackleAbility;
import models.entities.zombies.Abilities.IgnoreObstaclesAbility;
import models.entities.zombies.Abilities.KillPlantsAbility;
import models.entities.zombies.Abilities.LaserBeamAbility;
import models.entities.zombies.Abilities.PianoCrushAbility;
import models.entities.zombies.Abilities.PushIceAbility;
import models.entities.zombies.Abilities.RocketLaunchAbility;
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

    public static List<ZombieAbility> createAbilities(String objclass, GameSession gameSession) {
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
            case "ZombieIceAgeHunterProps":
                return abilities(new EatPlantAbility(), new ThrowIceAbility());
            case "ZombieIceAgeTroglobiteProps":
                return abilities(new EatPlantAbility(), new PushIceAbility());
            case "ZombieIceAgeDodoProps":
                return abilities(new EatPlantAbility(), new IgnoreObstaclesAbility());
            case "ZombieCrystalSkullProps":
                return abilities(new EatPlantAbility(), new LaserBeamAbility());
            case "ZombiePianoProps":
                return abilities(new EatPlantAbility(), new PianoCrushAbility());
            case "ZombieProspectorProps":
                return abilities(new EatPlantAbility(), new RocketLaunchAbility());
            case "ZombieArcadeProps":
                return abilities(new EatPlantAbility(), new ArcadePushAbility());
            case "ZombieModernAllStarProps":
                return abilities(new EatPlantAbility(), new FootballTackleAbility());
            case "ZombiePropertySheet":
            default:
                return abilities(new EatPlantAbility());
        }
    }

    private static List<ZombieAbility> abilities(ZombieAbility... items) {
        List<ZombieAbility> list = new ArrayList<>();
        for (ZombieAbility item : items) {
            list.add(item);
        }
        return list;
    }
}
