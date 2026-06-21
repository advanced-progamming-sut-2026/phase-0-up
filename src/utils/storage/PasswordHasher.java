package utils.storage;

import utils.Constants;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class PasswordHasher {

    private PasswordHasher() {}

    public static String hash(String raw){
        if (raw == null) {
            throw new IllegalArgumentException("Raw password cannot be null");
        }
        try {
            MessageDigest md = MessageDigest.getInstance(Constants.PASSWORD_HASH_ALGORITHM);

            byte[] encoded = md.digest(raw.getBytes(StandardCharsets.UTF_8));

            return bytesToHex(encoded);

        } catch (NoSuchAlgorithmException e){
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    public static boolean matches(String raw, String hashed){
        if (raw == null || hashed == null) {
            return false;
        }
        return hash(raw).equals(hashed);
    }

    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
