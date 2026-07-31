package models.quests;

import models.game.EnvironmentType;
import models.templates.QuestTemplate.ConditionSpec;

import java.util.regex.Matcher;

// Fills a quest's authored description with the concrete values from its condition spec, so the Travel
// Log never shows a raw placeholder. The bug this fixes: descriptions written with a bare "n" (e.g.
// "Win a level without losing more than n plants.") were printed literally instead of the real
// requirement ("... more than 5 plants."). Also injects a named season and the condition's plant /
// family / category targets where a description references them.
//
// Supported placeholders:
//   n , {n}      -> the numeric requirement (the condition's threshold, or its index when there is no
//                   threshold: "column n" / "row n" quests carry the value in index)
//   {plant}      -> the condition's target plant
//   {family}     -> the condition's target plant family
//   {category}   -> the condition's target plant category
//   {season}     -> the friendly season name (only via the season-aware overload)
public final class QuestText {
    private QuestText() { }

    public static String interpolate(String description, ConditionSpec spec) {
        return interpolate(description, spec, null);
    }

    public static String interpolate(String description, ConditionSpec spec, EnvironmentType season) {
        if (description == null) {
            return null;
        }
        String out = description;
        if (spec != null) {
            String n = String.valueOf(spec.getThreshold() != 0 ? spec.getThreshold() : spec.getIndex());
            // The braced form goes first. Braces are not word characters, so the 'n' in "{n}" is a
            // standalone word as far as the regex below is concerned: running that pass first turned
            // "{n}" into "{3000}" and left the braces on display in the travel log.
            out = replace(out, "{n}", n);
            // Standalone "n" only (word boundaries), so the 'n' inside "in", "column", "plants" is left
            // alone. quoteReplacement guards against any regex-special chars in the value.
            out = out.replaceAll("\\bn\\b", Matcher.quoteReplacement(n));
            out = replace(out, "{plant}", spec.getPlant());
            // Families and categories are authored as enum tokens (LOBBER, SUN_PRODUCER); show them the
            // way a player would say them ("Lobber", "Sun Producer").
            out = replace(out, "{family}", titleCase(spec.getFamily()));
            out = replace(out, "{category}", titleCase(spec.getCategory()));
            // Chapters are authored as enum-ish tokens (ANCIENT_EGYPT); show them the way the rest of
            // the game names a season ("Ancient Egypt").
            out = replace(out, "{chapter}", seasonName(EnvironmentType.fromChapter(spec.getChapter())));
        }
        if (season != null) {
            out = replace(out, "{season}", seasonName(season));
        }
        return out;
    }

    // Replaces a token only when there is a real value, so an absent value leaves the readable token in
    // place rather than injecting the literal "null".
    private static String replace(String text, String token, String value) {
        if (value == null || value.isBlank()) {
            return text;
        }
        return text.replace(token, value);
    }

    // Friendly, title-cased season name: FROSTBITE_CAVES -> "Frostbite Caves".
    public static String seasonName(EnvironmentType season) {
        return season == null ? "" : titleCase(season.name());
    }

    // ENUM_TOKEN -> "Enum Token". Returns the input unchanged when it is null or blank, so replace()
    // still leaves the readable placeholder in place rather than blanking it.
    public static String titleCase(String token) {
        if (token == null || token.isBlank()) {
            return token;
        }
        String[] parts = token.toLowerCase().trim().split("[_\\s]+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
