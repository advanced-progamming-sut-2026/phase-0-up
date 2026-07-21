package utils.regex;

public enum InGameRegex implements Regex{
    COLLECT_SUN("^\\s*collect\\s+sun\\s+-l\\s+\\(\\s*(?<x>\\d+)\\s*,\\s*(?<y>\\d+)\\s*\\)\\s*$"),
    SHOW_SUN_AMOUNT("^\\s*show\\s+sun\\s+amount\\s*$"),
    CHEAT_ADD_SUN("^\\s*cheat\\s+add\\s+-n\\s+(?<count>\\d+)\\s+suns\\s*$"),
    ADVANCE_TIME("^\\s*advance\\s+time\\s+-t\\s+(?<count>\\d+)\\s+ticks\\s*$"),
    PLANT_SEED("^\\s*plant\\s+plant\\s+-t\\s+(?<type>.+?)\\s+-l\\s+\\(\\s*(?<x>\\d+)\\s*,\\s*(?<y>\\d+)\\s*\\)\\s*$"),
    PLUCK_PLANT("^\\s*pluck\\s+plant\\s+-l\\s+\\(\\s*(?<x>\\d+)\\s*,\\s*(?<y>\\d+)\\s*\\)\\s*$"),
    FEED_PLANT("^\\s*feed\\s+plant\\s+-l\\s+\\(\\s*(?<x>\\d+)\\s*,\\s*(?<y>\\d+)\\s*\\)\\s*$"),
    CHEAT_REMOVE_COOLDOWN("^\\s*cheat\\s+remove-cooldown\\s*$"),
    CHEAT_ADD_PLANT_FOOD("^\\s*cheat\\s+add-plant-food\\s*$"),
    RELEASE_THE_NUKE("^\\s*release\\s+the\\s+nuke\\s*$"),
    SHOW_MAP("^\\s*show\\s+map\\s*$"),
    SHOW_PLANTS_STATUS("^\\s*show\\s+plants\\s+status\\s*$"),
    SHOW_TILE_STATUS("^\\s*show\\s+tile\\s+status\\s+-l\\s+\\(\\s*(?<x>\\d+)\\s*,\\s*(?<y>\\d+)\\s*\\)\\s*$"),
    BREAK_VASE("^\\s*break\\s+vase\\s+-l\\s+\\(\\s*(?<x>\\d+)\\s*,\\s*(?<y>\\d+)\\s*\\)\\s*$"),
    COLLECT_SEED("^\\s*collect\\s+seed\\s+-l\\s+\\(\\s*(?<x>\\d+)\\s*,\\s*(?<y>\\d+)\\s*\\)\\s*$"),
    BOWL_NUT("^\\s*bowl\\s+-t\\s+(?<type>\\S+)\\s+-l\\s+\\(\\s*(?<x>\\d+)\\s*,\\s*(?<y>\\d+)\\s*\\)\\s*$"),
    SUMMON_ZOMBIE("^\\s*summon\\s+-t\\s+(?<type>\\S+)\\s+-l\\s+\\(\\s*(?<x>\\d+)\\s*,\\s*(?<y>\\d+)\\s*\\)\\s*$"),
    ZOMBIES_INFO("^\\s*zombies\\s+info\\s*$"),
    // -l accepts the parenthesized (x, y) used by every other in-game location command; the parens are
    // optional so "-l 3, 2" is also accepted. x is the column, y is the row.
    CHEAT_SPAWN_ZOMBIE("^\\s*cheat\\s+spawn-zombie\\s+-t\\s+(?<type>\\S+)\\s+-l\\s+\\(?(?<x>\\d+),\\s*(?<y>\\d+)\\)?\\s*$"),
    SWAP_PLANTS("^\\s*swap\\s+-l\\s+\\(\\s*(?<x1>\\d+)\\s*,\\s*(?<y1>\\d+)\\s*\\)\\s+\\(\\s*(?<x2>\\d+)\\s*,\\s*(?<y2>\\d+)\\s*\\)\\s*$"),
    UPGRADE_PLANT("^\\s*upgrade\\s+-t\\s+(?<type>.+?)\\s*$");

    private final String pattern;

    InGameRegex(String pattern) {
        this.pattern = pattern;
    }

    @Override
    public String getPattern() {
        return pattern;
    }

}
