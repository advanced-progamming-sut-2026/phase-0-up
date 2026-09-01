package models.entities.zombies;

import models.game.EnvironmentType;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

// Which Zomboss a season fields, and what it can do.
//
// One per chapter, chosen by the chapter rather than authored in levels.json: a boss IS its world --
// the Tuskmaster only makes sense standing in Frostbite Caves -- so letting a level file pick one
// would only ever be a way to get it wrong. LevelFactory therefore builds the mode from the template's
// chapter and nothing else.
//
// ## The two flags
//
// `shiftsRows` and `spawnsZombies` are not decoration, they are the spec's one explicit exception:
// the Frostbite Mammoth neither moves between rows nor summons anything, while the other three do
// both. The asset dump agrees, which is the useful confirmation that the exception is the real game's
// and not a slip -- ZOMBIE_ICEAGE_ZOMBOSS is the ONLY one of the four whose animation ships no walk
// clips and no portal/summon clip. The Sphinx has walk_up/walk_down and zombie_portal_*, the Dragon
// has summoning, the Shark has spawn; the Mammoth has wind, slingshot and glacier_column and stands
// exactly still. See views.gdx.sprite.SpriteRegistry for the animation names.
public enum BossKind {

    SPHINX("ZombieEgyptZomboss", "Zombot Sphinx-inator", EnvironmentType.ANCIENT_EGYPT,
            true, true, BossAttack.MISSILE, BossAttack.DASH),

    MAMMOTH("ZombieIceAgeZomboss", "Zombot Tuskmaster", EnvironmentType.FROSTBITE_CAVES,
            false, false, BossAttack.ICE_MISSILE, BossAttack.ICE_WIND, BossAttack.FREEZE_COLUMN),

    SHARK("ZombieBeachZomboss", "Zombot Sharktronic Sub", EnvironmentType.BIG_WAVE_BEACH,
            true, true, BossAttack.BABY_SHARKS, BossAttack.TURBINE),

    DRAGON("ZombieDarkZomboss", "Zombot Dark Dragon", EnvironmentType.DARK_AGES,
            true, true, BossAttack.FIREBALL, BossAttack.ROW_BURN);

    private final String alias;
    private final String displayName;
    private final EnvironmentType season;
    private final boolean shiftsRows;
    private final boolean spawnsZombies;
    private final List<BossAttack> attacks;

    BossKind(String alias, String displayName, EnvironmentType season,
             boolean shiftsRows, boolean spawnsZombies, BossAttack... attacks) {
        this.alias = alias;
        this.displayName = displayName;
        this.season = season;
        this.shiftsRows = shiftsRows;
        this.spawnsZombies = spawnsZombies;
        this.attacks = Collections.unmodifiableList(Arrays.asList(attacks));
    }

    // The name the rest of the game knows this zombie by: what Zomboss.getAlias() answers, what the
    // narration prints, and the key SpriteRegistry resolves to a .PAM.
    public String getAlias() {
        return alias;
    }

    public String getDisplayName() {
        return displayName;
    }

    public EnvironmentType getSeason() {
        return season;
    }

    public boolean shiftsRows() {
        return shiftsRows;
    }

    public boolean spawnsZombies() {
        return spawnsZombies;
    }

    public List<BossAttack> getAttacks() {
        return attacks;
    }

    // The boss that belongs to this season. Never null -- an unrecognised chapter gets the Sphinx, for
    // the same reason EnvironmentType.fromChapter falls back to Ancient Egypt.
    public static BossKind forSeason(EnvironmentType season) {
        for (BossKind kind : values()) {
            if (kind.season == season) {
                return kind;
            }
        }
        return SPHINX;
    }

    // Looks a boss up by the name its narration uses, or null if that is not a boss's name.
    //
    // The model writes sentences the way a player would read them -- "The Zombot Sphinx-inator fires a
    // missile" -- so anything reacting to those sentences has the DISPLAY name and needs the alias,
    // which is what the sprite layer and the entity itself are keyed on. See views.gdx.render
    // .ZombieActions, which plays each boss's own attack animation off exactly these sentences.
    public static BossKind forDisplayName(String displayName) {
        if (displayName == null) {
            return null;
        }
        String wanted = displayName.trim();
        for (BossKind kind : values()) {
            if (kind.displayName.equalsIgnoreCase(wanted)) {
                return kind;
            }
        }
        return null;
    }

    // Looks a boss up by the alias it fights under, or null for any other zombie. The view asks this to
    // find out whether the thing it is about to draw is a boss.
    public static BossKind forAlias(String alias) {
        if (alias == null) {
            return null;
        }
        for (BossKind kind : values()) {
            if (kind.alias.equalsIgnoreCase(alias.trim())) {
                return kind;
            }
        }
        return null;
    }
}
