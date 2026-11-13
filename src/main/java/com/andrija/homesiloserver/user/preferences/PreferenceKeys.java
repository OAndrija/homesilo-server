package com.andrija.homesiloserver.user.preferences;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The settings the server keeps, and what each may be set to. Choices from a
 * short fixed list are checked exactly; open-ended ones (theme and accent names,
 * which the app adds to over time) only by their shape, as with dashboard tiles.
 */
final class PreferenceKeys {

    /** A setting's name pattern, the values it accepts, and how to say so when one doesn't fit. */
    record Rule(Pattern key, Pattern value, String message) {
        boolean accepts(String value) {
            return this.value.matcher(value).matches();
        }
    }

    private static final String NAME = "[a-z][a-z0-9-]{0,39}";
    private static final String PAGE = "[a-z][a-z0-9-]{0,29}";

    private static final List<Rule> RULES = List.of(
            rule("theme\\.mode", "light|dark|system", "Theme mode must be light, dark or system"),
            rule("theme\\.(light|dark)", NAME, "Theme names are lowercase letters, digits and dashes"),
            rule("appearance\\.accent", NAME, "Accent names are lowercase letters, digits and dashes"),
            rule("appearance\\.corners", "sharp|subtle|rounded|round",
                    "Corners must be sharp, subtle, rounded or round"),
            rule("appearance\\.spacing", "compact|comfortable|spacious",
                    "Spacing must be compact, comfortable or spacious"),
            rule("appearance\\.cards", "vivid|flat", "Cards must be vivid or flat"),
            rule("appearance\\.font", "inter|system|readable", "Font must be inter, system or readable"),
            rule("appearance\\.motion", "system|full|reduced", "Motion must be system, full or reduced"),
            rule("view\\." + PAGE, "list|grid", "A view must be list or grid"),
            rule("sort\\." + PAGE, "[a-zA-Z]{1,30},(asc|desc)",
                    "A sort is a column and a direction, like \"name,asc\""));

    /** More than the app uses, so new settings fit without a server change. */
    static final int MAX_VALUES = 64;

    private PreferenceKeys() {
    }

    static Optional<Rule> ruleFor(String key) {
        return RULES.stream().filter(r -> r.key().matcher(key).matches()).findFirst();
    }

    private static Rule rule(String key, String value, String message) {
        return new Rule(Pattern.compile(key), Pattern.compile(value), message);
    }
}
