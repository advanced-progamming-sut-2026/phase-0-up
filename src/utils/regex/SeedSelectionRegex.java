package utils.regex;

public enum SeedSelectionRegex implements Regex{
    SHOW_ALL_PLANTS("^\\s*show\\s+all\\s+plants\\s*$"),
    SHOW_AVAILABLE_PLANTS("^\\s*show\\s+available\\s+plants\\s*$"),
    ADD_PLANT("^\\s*add\\s+plant\\s+-t\\s+(?<type>.+?)\\s*$"),
    REMOVE_PLANT("^\\s*remove\\s+plant\\s+-t\\s+(?<type>.+?)\\s*$"),
    // The Imitater's second half: which plant it copies. A verb of its own rather than an extra flag on
    // "add plant", because it is a different question -- the named plant is not the one being added.
    IMITATE_PLANT("^\\s*imitate\\s+plant\\s+-t\\s+(?<type>.+?)\\s*$"),
    BOOST_PLANT("^\\s*boost\\s+plant\\s+-t\\s+(?<type>.+?)\\s*$"),
    START_GAME("^\\s*start\\s+game\\s*$");


    private final String pattern;

    SeedSelectionRegex(String pattern) {
        this.pattern = pattern;
    }

    @Override
    public String getPattern() {
        return pattern;
    }
}
