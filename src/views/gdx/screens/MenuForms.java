package views.gdx.screens;

import views.gdx.core.GdxContext;

// Field checks that have to happen before a form becomes a command string.
//
// These are NOT the game's validation rules -- UsernameValidator, PasswordValidator and the rest still
// run inside the Commands, on both builds. These guard the one thing a form can break that a prompt
// cannot: the command grammar splits every value on whitespace, so a nickname with a space in it would
// not fail validation, it would parse as a different command and come back as "invalid command".
//
// Saying "nicknames have no spaces" is the difference between a fixable mistake and a mystery.
final class MenuForms {

    private MenuForms() {
    }

    static boolean require(GdxContext context, String value, String message) {
        if (value == null || value.isBlank()) {
            context.toasts().error(message);
            return false;
        }
        return true;
    }

    static boolean noSpaces(GdxContext context, String value, String message) {
        if (value != null && value.chars().anyMatch(Character::isWhitespace)) {
            context.toasts().error(message);
            return false;
        }
        return true;
    }

    // Both at once, which is what nearly every caller wants.
    static boolean filled(GdxContext context, String value, String label) {
        return require(context, value, "Enter your " + label + ".")
                && noSpaces(context, value, "Your " + label + " can't contain spaces.");
    }
}
