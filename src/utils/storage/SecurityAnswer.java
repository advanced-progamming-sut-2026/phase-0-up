package utils.storage;

import java.util.Locale;

// The single source of truth for how a security answer is turned into the string that gets hashed.
// Registration and password recovery MUST both go through here: a hash is one-way, so if the two
// sides normalize differently the stored digest can never be reproduced and every correct answer is
// rejected. That asymmetry (registration hashed the raw answer, recovery hashed a trimmed+lowercased
// one) was exactly the "forgot password always says wrong answer" bug.
public final class SecurityAnswer {

    private SecurityAnswer() {}

    // Canonical form of a typed answer: quotes stripped, edges trimmed, internal whitespace runs
    // collapsed, lower-cased. Locale.ROOT is deliberate -- Locale-sensitive lowercasing maps 'I' to a
    // dotless 'i' under a Turkish default locale, which would make the stored hash machine-dependent.
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim();

        // The recovery pattern accepts a quoted answer ("first pet") and hands back the quotes as part
        // of the captured group, so they have to come off before hashing.
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }

        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    // True when the answer, once normalized, is not usable as a secret.
    public static boolean isBlank(String raw) {
        return normalize(raw).isEmpty();
    }

    // The digest to store at registration time.
    public static String hash(String raw) {
        return PasswordHasher.hash(normalize(raw));
    }

    // Do two typed answers (the answer and its confirmation) mean the same thing?
    public static boolean sameAnswer(String first, String second) {
        return normalize(first).equals(normalize(second));
    }

    // Verify a typed answer against a stored digest.
    //
    // Accounts registered before the normalization fix hold a digest of the *raw* answer, which the
    // canonical hash can never reproduce. So a stored digest is also compared against the raw typed
    // answer; callers use wasLegacyMatch() to detect that case and re-hash the account in canonical
    // form, which quietly migrates old users the first time they recover a password.
    public static boolean matches(String typedAnswer, String storedHash) {
        if (storedHash == null) {
            return false;
        }
        return PasswordHasher.matches(normalize(typedAnswer), storedHash)
                || wasLegacyMatch(typedAnswer, storedHash);
    }

    // True when the digest only lines up with the pre-fix (un-normalized) hashing scheme.
    public static boolean wasLegacyMatch(String typedAnswer, String storedHash) {
        if (typedAnswer == null || storedHash == null) {
            return false;
        }
        if (PasswordHasher.matches(normalize(typedAnswer), storedHash)) {
            return false;   // already canonical, nothing to migrate
        }
        String trimmed = typedAnswer.trim();
        String unquoted = trimmed;
        if (unquoted.length() >= 2 && unquoted.startsWith("\"") && unquoted.endsWith("\"")) {
            unquoted = unquoted.substring(1, unquoted.length() - 1);
        }
        return PasswordHasher.matches(trimmed, storedHash)
                || PasswordHasher.matches(unquoted, storedHash);
    }
}
