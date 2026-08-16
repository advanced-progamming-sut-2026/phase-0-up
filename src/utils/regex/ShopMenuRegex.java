package utils.regex;

public enum ShopMenuRegex implements Regex{
    SHOP_LIST("\\s*shop\\s+list\\s*"),
    SHOP_DAILY("\\s*shop\\s+daily\\s*"),
    // -t takes a lazy .+? rather than \S+, the same way CollectionMenuRegex's -p does.
    //
    // \S+ cannot match a plant whose name has a space in it, which is half the roster -- "shop buy -i 3
    // -n 1 -t Snow Pea" simply did not parse, so the selective seed packet was unbuyable for Snow Pea,
    // Twin Sunflower, Gold Bloom and the rest, in the terminal as well as the GUI. -t is the last flag
    // and the pattern is anchored, so the lazy group has nothing after it to swallow.
    BUY("^\\s*shop\\s+buy\\s+-i\\s+(?<id>\\d+)\\s+-n\\s+(?<number>\\d+)(?:\\s+-t\\s+(?<plantType>.+?))?\\s*$");

    private final String pattern;

    ShopMenuRegex(String pattern) {
        this.pattern = pattern;
    }
    @Override
    public String getPattern() {
        return pattern;
    }
}
