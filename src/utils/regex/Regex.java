package utils.regex;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public interface Regex {
    String getPattern();

    default Matcher getMatcher(String input) {
        return Pattern.compile(getPattern()).matcher(input);
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