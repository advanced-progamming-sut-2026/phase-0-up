package utils.regex;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public interface Regex {
    String getPattern();

    // Every command is matched against the trimmed input, so stray leading/trailing spaces or tabs can
    // never turn a valid command into "Invalid Command" -- regardless of whether an individual pattern
    // remembered to allow for them. Spacing *between* words is already free: the patterns separate
    // tokens with \s+, which soaks up any run of spaces/tabs. Only the outer edges are normalized, so
    // captured groups keep their exact value. A null input matches nothing rather than throwing.
    default Matcher getMatcher(String input) {
        return Pattern.compile(getPattern()).matcher(input == null ? "" : input.trim());
    }

    default boolean matches(String input) {
        return getMatcher(input).matches();
    }

    default String getGroup(String input, String group) {
        Matcher matcher = getMatcher(input);
        if (matcher.matches()) return matcher.group(group);
        return null;
    }
}