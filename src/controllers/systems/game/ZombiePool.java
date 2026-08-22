package controllers.systems.game;

import models.game.EnvironmentType;
import models.templates.ZombieTemplate;
import utils.Constants;
import utils.registry.ZombieRegistry;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

// Which zombies a wave may field, and how likely each of them is.
//
// This exists because the rosters in data/levels.json are the same three lines copied sixteen times:
// every level opens with exactly two types, repeats one mid-wave roster two or three times, then dumps
// the chapter's whole bestiary into the last two waves. That shape has two problems the authored lists
// cannot fix on their own.
//
//   * NO VARIETY EARLY. Waves 1 and 2 of all sixteen levels field two types, and eight zombies in the
//     registry are never fielded by any level at all.
//   * NO CURVE INSIDE A WAVE'S POOL. WaveSystem drew UNIFORMLY from the wave's list, so in an Egypt
//     wave 3 a 100-point Browncoat and a 250-point Explorer were equally likely, and in a Beach wave 1
//     -- authored as {Browncoat, Fisherman} -- a 700-point Fisherman was as likely as a 100-point
//     Browncoat. That is where the difficulty spikes came from: not from how MANY zombies a wave
//     fielded but from WHICH ones it happened to roll.
//
// So a wave's roster is the union of what the level authored and what this chapter's tier table offers
// at that point in the level, and the draw is weighted by cost. Union rather than replacement: a level
// that deliberately authors an odd roster (the special levels, the mini-games) keeps everything it
// asked for, and nothing that could spawn before stops being able to.
//
// Model/controller only -- no rendering, no LibGDX. Every number here is game design, not presentation.
public final class ZombiePool {

    // How far into a level a tier becomes available, as a fraction of the level's waves.
    //
    // BASIC is always open, so a late wave can still send Browncoats -- a wave of nothing but elites is
    // a wall rather than a wave. ELITE opens at three quarters, which is the final wave of a four-wave
    // level and the last two of a seven-wave one.
    private static final double ARMORED_FROM = 0.15;
    private static final double SPECIAL_FROM = 0.45;
    private static final double ELITE_FROM = 0.75;

    // Zombies with no world of their own in this game: Brick is the third armor step (the roster ships
    // Cone and Bucket and simply skipped it) and Newspaper is a plain lawn zombie, so both read
    // correctly on any ground and belong to every chapter.
    private static final List<String> GENERIC_ARMORED = List.of("ZombieArmor1", "ZombieArmor2");
    private static final List<String> GENERIC_SPECIAL = List.of("ZombieArmor4", "ZombieNewspaper");

    // The six zombies whose own worlds (Lost City, Wild West, Neon Mixtape / Modern) this game does not
    // ship were fielded by no level at all. Rather than leave 22% of the bestiary unused, each has been
    // adopted by the chapter it fits best -- read the per-tier comments below for the reasoning. Every
    // one keeps its own abilities and its own art; only its home changed. They are placed individually
    // rather than added to the GENERIC lists above precisely because none of them is generic.

    private ZombiePool() { }

    // The aliases this chapter offers at `progress` through the level, cheapest tier first.
    public static List<String> rosterFor(EnvironmentType environment, double progress) {
        List<String> roster = new ArrayList<>(basics(environment));
        if (progress >= ARMORED_FROM) {
            roster.addAll(GENERIC_ARMORED);
            roster.addAll(armored(environment));
        }
        if (progress >= SPECIAL_FROM) {
            roster.addAll(GENERIC_SPECIAL);
            roster.addAll(specials(environment));
        }
        if (progress >= ELITE_FROM) {
            roster.addAll(elites(environment));
        }
        return roster;
    }

    // The Browncoat is every chapter's floor: cheap, harmless alone, and the thing the player learns to
    // read every other zombie against.
    private static List<String> basics(EnvironmentType environment) {
        if (environment == EnvironmentType.DARK_AGES) {
            // The Imp Dragon costs 150 -- Dark Ages' own cheap filler, and the chapter's only zombie
            // under 450 apart from the Browncoat. Without it that chapter has no low tier to bias
            // toward and its opening waves are 550-point Knights.
            return List.of("ZombieDefault", "ZombieDarkImpDragon");
        }
        return List.of("ZombieDefault");
    }

    private static List<String> armored(EnvironmentType environment) {
        if (environment == null) {
            return List.of();
        }
        return switch (environment) {
            // Prospector: a pickaxe-and-dynamite miner belongs in the excavation chapter, and its
            // CarryADynamite blasts it to the FAR LEFT of the lawn to walk back from there -- which is
            // the same "part of the wave is already behind you" pressure Egypt's tornado exists to
            // create. At 200 it also fills the gap between Ra (100) and Explorer (250).
            case ANCIENT_EGYPT -> List.of("ZombieRa", "ZombieExplorer", "ZombieProspector");
            case FROSTBITE_CAVES -> List.of("ZombieIceAgeHunter");
            // Jane: the art is a lady with a PARASOL, not the jungle adventurer the alias suggests --
            // which makes her a better beach fit than expected, since a parasol at the seaside needs no
            // explaining. It also makes DeflectLobbedAbility literal rather than arbitrary: the parasol
            // is what swats the shot away. Pointed at this chapter specifically, because water lanes
            // push the player onto lobbed shots (Cabbage-pult, Melon-pult) and she asks a real question
            // of that loadout.
            case BIG_WAVE_BEACH -> List.of("ZombieBeachSnorkel", "ZombieLostCityJane");
            case DARK_AGES -> List.of("ZombieDarkArmor3");
        };
    }

    private static List<String> specials(EnvironmentType environment) {
        if (environment == null) {
            return List.of();
        }
        return switch (environment) {
            case ANCIENT_EGYPT -> List.of("ZombieTombRaiser", "ZombieImp");
            case FROSTBITE_CAVES -> List.of("ZombieIceAgeDodo", "ZombieIceAgeTroglobite");
            // Arcade: a seaside boardwalk arcade is a real pairing, and ArcadePushAbility gives the
            // beach the one thing its roster lacks -- a LAND problem. Snorkel, Fisherman and Octopus are
            // all water threats, so a shield being pushed up a dry lane is a different question.
            case BIG_WAVE_BEACH -> List.of("ZombieBeachFisherman", "ZombieArcade");
            // Piano: the nearest thing this game has to a gothic keyboard, and PianoCrushAbility +
            // ChangeRow is a threat Dark Ages has none of -- it FLATTENS plants and wanders lanes
            // instead of stopping to eat, so a wall it cannot be blocked by.
            // Crystal Skull: a cursed relic that fires a beam and drains sun. Dark Ages is the magic
            // chapter (Wizard, Dark King, necromancy tiles), and Egypt -- the other candidate -- already
            // has Ra doing sun theft, so putting it there would have doubled up a mechanic.
            case DARK_AGES -> List.of("ZombieDarkJuggler", "ZombieWizard",
                    "ZombiePiano", "ZombieCrystalSkull");
        };
    }

    // The wave-ender of each chapter. Gargantuar is in every one because it is the game's universal
    // "you are out of time" zombie.
    private static List<String> elites(EnvironmentType environment) {
        List<String> elites = new ArrayList<>();
        elites.add("ZombieGargantuar");
        if (environment == EnvironmentType.BIG_WAVE_BEACH) {
            elites.add("ZombieBeachOctopus");
        } else if (environment == EnvironmentType.DARK_AGES) {
            elites.add("ZombieDarkKing");
        } else if (environment == EnvironmentType.FROSTBITE_CAVES) {
            // Frostbite had NOTHING between the 600-point Troglobite and the 1500-point Gargantuar,
            // which is the widest hole in any chapter: its finale jumped straight from a mid-tier to
            // the wave-ender. The All-Star at 1000 fills it, and a fast tackler is a different problem
            // from everything else that chapter fields -- Hunter, Dodo and Troglobite are all slow
            // obstacle-and-ice threats.
            elites.add("ZombieModernAllStar");
        }
        return elites;
    }

    // The wave's roster: what the level authored, plus what the chapter offers this far in, duplicates
    // collapsed. Insertion order is preserved so a seeded Random still reproduces a run exactly.
    public static List<String> resolveAliases(List<String> authored, EnvironmentType environment,
                                             double progress) {
        Set<String> merged = new LinkedHashSet<>();
        if (authored != null) {
            for (String alias : authored) {
                if (alias != null && !alias.isBlank()) {
                    merged.add(alias);
                }
            }
        }
        merged.addAll(rosterFor(environment, progress));
        return new ArrayList<>(merged);
    }

    // Registry lookup for a roster. Unknown aliases and non-positive costs are dropped -- a free zombie
    // would let WaveSystem's purchase loop run forever.
    public static List<ZombieTemplate> resolveTemplates(List<String> aliases) {
        List<ZombieTemplate> pool = new ArrayList<>();
        if (aliases == null) {
            return pool;
        }
        for (String alias : aliases) {
            if (alias == null) {
                continue;
            }
            ZombieTemplate template = ZombieRegistry.getInstance().getZombieTemplateByAlias(alias);
            if (template != null && template.getWavePointCost() > 0) {
                pool.add(template);
            }
        }
        return pool;
    }

    // Picks one of `candidates`, favouring cheap zombies early in the level and dear ones late.
    //
    // A zombie's tier is read off its WAVE POINT COST relative to the rest of the pool rather than from
    // a hand-written table of 27 entries: cost already IS the game's own statement of how dangerous a
    // zombie is, so a table would be a second opinion to keep in sync every time one is added or
    // retuned.
    //
    //   t = 0 for the cheapest in the pool, 1 for the dearest
    //   weight = 1 + BIAS * ((1-t)(1-p) + t*p)
    //
    // At p = 0 the cheapest carries 1 + BIAS and the dearest carries 1; at p = 1 they swap; mid-level
    // everything sits near 1 + BIAS/2. No weight is ever zero, so a wave can always still field
    // anything its roster allows -- the curve shifts the odds, it does not gate the roster. Gating is
    // rosterFor's job, and keeping the two separate is what stops a late wave from being unable to
    // field a Browncoat.
    //
    // `candidates` is WaveSystem's already-filtered affordable list, so this cannot break its
    // spend-the-budget-exactly invariant: it only ever chooses among picks that were already legal.
    public static ZombieTemplate pick(List<ZombieTemplate> candidates, List<ZombieTemplate> wholePool,
                                      double progress, Random random) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        if (candidates.size() == 1) {
            return candidates.get(0);
        }
        int cheapest = Integer.MAX_VALUE;
        int dearest = 0;
        for (ZombieTemplate template : wholePool) {
            cheapest = Math.min(cheapest, template.getWavePointCost());
            dearest = Math.max(dearest, template.getWavePointCost());
        }
        // A pool whose zombies all cost the same has no tiers to bias, so fall back to a flat draw.
        if (dearest <= cheapest) {
            return candidates.get(random.nextInt(candidates.size()));
        }

        double p = Math.min(1d, Math.max(0d, progress));
        double total = 0d;
        double[] weights = new double[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            double span = dearest - cheapest;
            double t = (candidates.get(i).getWavePointCost() - cheapest) / span;
            weights[i] = 1d + Constants.ZOMBIE_TIER_BIAS * ((1d - t) * (1d - p) + t * p);
            total += weights[i];
        }

        double roll = random.nextDouble() * total;
        for (int i = 0; i < weights.length; i++) {
            roll -= weights[i];
            if (roll <= 0d) {
                return candidates.get(i);
            }
        }
        // Floating-point slack only; the loop above normally returns.
        return candidates.get(candidates.size() - 1);
    }
}
