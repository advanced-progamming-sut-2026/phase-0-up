package utils.regex;

public enum CollectionMenuRegex implements Regex{
    SHOW_PLANTS("^\\s*menu\\s+collection\\s+show-plants\\s*$"),
    SHOW_ALL_PLANTS("^\\s*menu\\s+collection\\s+show-all-plants\\s*$"),
    SHOW_ZOMBIES("^\\s*menu\\s+collection\\s+show-zombies\\s*$"),
    SHOW_ALL_ZOMBIES("^\\s*menu\\s+collection\\s+show-all-zombies\\s*$"),
    SHOW_PLANT_DETAIL ("^\\s*menu\\s+collection\\s+show-plant\\s+-p\\s+(?<plantName>.+?)\\s*$"),
    SHOW_ZOMBIE_DETAIL("^\\s*menu\\s+collection\\s+show-zombie\\s+-z\\s+(?<zombieName>.+?)\\s*$"),
    UPGRADE_PLANT("^\\s*menu\\s+collection\\s+upgrade-plant\\s+-p\\s+(?<plantName>.+?)\\s*$"),
    PURCHASE_PLANT("^\\s*menu\\s+collection\\s+purchase-plant\\s+-p\\s+(?<plantName>.+?)\\s*$");


    private final String pattern;

    CollectionMenuRegex(String pattern) {
        this.pattern = pattern;
    }

    @Override
    public String getPattern() {
        return this.pattern;
    }
}
