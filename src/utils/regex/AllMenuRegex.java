package utils.regex;

public enum AllMenuRegex implements Regex{
    EXIT_MENU("^\\s*menu\\s+exit\\s*$"),
    ENTER_MENU("^\\s*menu\\s+enter\\s+(?<menuName>\\S+)\\s*$"),
    SHOW_CURRENT("^\\s*menu\\s+show\\s+current\\s*$"),
    // Shuts the whole application down from any menu. Deliberately distinct from "menu exit" (which
    // only steps back one menu) and from the in-game "exit game" (which abandons a match): neither of
    // those strings can match this pattern, so no command changes meaning.
    EXIT_APPLICATION("^\\s*(?:exit|quit)(?:\\s+application)?\\s*$");


    private final String pattern;

    AllMenuRegex(String pattern) {
        this.pattern = pattern;
    }

    @Override
    public String getPattern() {
        return this.pattern;
    }
}
