package utils.regex;

public enum ShopMenuRegex implements Regex{
    SHOP_LIST("\\s*shop\\s+list\\s*"),
    SHOP_DAILY("\\s*shop\\s+daily\\s*"),
    BUY("^\\s*shop\\s+buy\\s+-i\\s+(?<id>\\d+)\\s+-n\\s+(?<number>\\d+)(?:\\s+-t\\s+(?<plantType>\\S+))?\\s*$");

    private final String pattern;

    ShopMenuRegex(String pattern) {
        this.pattern = pattern;
    }
    @Override
    public String getPattern() {
        return pattern;
    }
}
