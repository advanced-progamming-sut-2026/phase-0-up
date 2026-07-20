package utils.regex;

public enum InGameRegex implements Regex{
    COLLECT_SUN("^\\s*collect\\s+sun\\s+-l\\s+\\((?<x>\\d+),\\s*(?<y>\\d+)\\)\\s*$"),
    SHOW_SUN_AMOUNT("^\\s*show\\s+sun\\s+amount\\s*$"),
    CHEAT_ADD_SUN("^\\s*cheat\\s+add\\s+-n\\s+(?<count>\\d+)\\s+suns\\s*$"),
    ADVANCE_TIME("^\\s*advance\\s+time\\s+-t\\s+(?<count>\\d+)\\s+ticks\\s*$"),
    PLANT_SEED("^\\s*plant\\s+plant\\s+-t\\s+(?<type>\\S+)\\s+-l\\s+\\((?<x>\\d+),\\s*(?<y>\\d+)\\)\\s*$"),
    PLUCK_PLANT("^\\s*pluck\\s+plant\\s+-l\\s+\\((?<x>\\d+),\\s*(?<y>\\d+)\\)\\s*$"),
    FEED_PLANT("^\\s*feed\\s+plant\\s+-l\\s+\\((?<x>\\d+),\\s*(?<y>\\d+)\\)\\s*$"),
    CHEAT_REMOVE_COOLDOWN("^\\s*cheat\\s+remove-cooldown\\s*$"),
    RELEASE_THE_NUKE("^\\s*release\\s+the\\s+nuke\\s*$"),
    SHOW_MAP("^\\s*show\\s+map\\s*$"),
    SHOW_PLANTS_STATUS("^\\s*show\\s+plants\\s+status\\s*$"),
    SHOW_TILE_STATUS("^\\s*show\\s+tile\\s+status\\s+-l\\s+\\((?<x>\\d+),\\s*(?<y>\\d+)\\)\\s*$"),
    BREAK_VASE("^\\s*break\\s+vase\\s+-l\\s+\\((?<x>\\d+),\\s*(?<y>\\d+)\\)\\s*$"),
    COLLECT_SEED("^\\s*collect\\s+seed\\s+-l\\s+\\((?<x>\\d+),\\s*(?<y>\\d+)\\)\\s*$"),
    BOWL_NUT("^\\s*bowl\\s+-t\\s+(?<type>\\S+)\\s+-l\\s+\\((?<x>\\d+),\\s*(?<y>\\d+)\\)\\s*$"),
    SUMMON_ZOMBIE("^\\s*summon\\s+-t\\s+(?<type>\\S+)\\s+-l\\s+\\((?<x>\\d+),\\s*(?<y>\\d+)\\)\\s*$"),
    SWAP_PLANTS("^\\s*swap\\s+-l\\s+\\((?<x1>\\d+),\\s*(?<y1>\\d+)\\)\\s+\\((?<x2>\\d+),\\s*(?<y2>\\d+)\\)\\s*$"),
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
